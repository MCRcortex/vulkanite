package me.cortex.vulkanite.acceleration;


//Multithreaded acceleration manager, builds blas's in a seperate queue,
// then memory copies over to main, while doing compaction

import me.cortex.vulkanite.compat.IAccelerationBuildResult;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.cmd.VCommandPool;
import me.cortex.vulkanite.lib.memory.MemoryManager;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.VQueryPool;
import me.cortex.vulkanite.lib.other.sync.VFence;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL45.nglNamedBufferData;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.*;

public class AccelerationBlasBuilder {
    private final VContext context;
    private record BLASTriangleData(int quadCount, NativeBuffer geometry, int geometryFlags) {}
    private record BLASBuildJob(List<BLASTriangleData> geometries, JobPassThroughData data) {}
    public record BLASBuildResult(VAccelerationStructure structure, JobPassThroughData data) {}
    public record BLASBatchResult(List<BLASBuildResult> results, VSemaphore semaphore) { }
    private final Thread worker;
    private final int asyncQueue;
    private final Consumer<BLASBatchResult> resultConsumer;
    private final VCommandPool sinlgeUsePool;

    private final VQueryPool queryPool;

    //TODO: maybe move to an executor type system
    private final Semaphore awaitingJobBatchess = new Semaphore(0);//Note: this is done to avoid spin locking on the job consumer
    private final ConcurrentLinkedDeque<List<BLASBuildJob>> batchedJobs = new ConcurrentLinkedDeque<>();

    public AccelerationBlasBuilder(VContext context, int asyncQueue, Consumer<BLASBatchResult> resultConsumer) {
        this.sinlgeUsePool = context.cmd.createSingleUsePool();
        this.queryPool = new VQueryPool(context.device, 10000, VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_KHR);
        this.context = context;
        this.asyncQueue = asyncQueue;
        this.resultConsumer = resultConsumer;
        worker = new Thread(this::run);
        worker.setName("Acceleration blas worker");
        worker.start();
    }

    //The acceleration manager blas builder runs in batches/groups
    private void run() {
        MemoryStack bigStack = MemoryStack.create(20_000_000);

        List<BLASBuildJob> jobs = new ArrayList<>();
        while (true) {
            {
                jobs.clear();
                try {
                    awaitingJobBatchess.acquire();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                int i = -1;
                //Collect the job batch
                while (!this.batchedJobs.isEmpty()) {
                    i++;
                    jobs.addAll(this.batchedJobs.poll());
                }
                if (i > 0) {
                    //This updates the semaphore to be accurate, should be able to grab exactly i permits (number of batches)
                    try {
                        awaitingJobBatchess.acquire(i);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            sinlgeUsePool.doReleases();
            if (jobs.size() > 100) {
                System.err.println("EXCESSIVE JOBS FOR SOME REASON AAAAAAAAAA");
                //while (true);
            }
            //Jobs are batched and built on the async vulkan queue then block synchronized with fence
            // which then results in compaction and dispatch to consumer

            //TODO: clean up this spaghetti shithole
            try (var stack = bigStack.push()) {
                var buildInfos = VkAccelerationStructureBuildGeometryInfoKHR.calloc(jobs.size(), stack);
                PointerBuffer buildRanges = stack.mallocPointer(jobs.size());
                LongBuffer pAccelerationStructures = stack.mallocLong(jobs.size());

                List<VBuffer> buffersToFree = new ArrayList<>(jobs.size() * 2);
                var scratchBuffers = new VBuffer[jobs.size()];
                var accelerationStructures = new VAccelerationStructure[jobs.size()];

                var uploadBuildCmd = sinlgeUsePool.createCommandBuffer();
                uploadBuildCmd.begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

                //Fill in the buildInfo and buildRanges
                int i = -1;
                for (var job : jobs) {
                    i++;
                    var brs = VkAccelerationStructureBuildRangeInfoKHR.calloc(job.geometries.size(), stack);
                    var geometryInfos = VkAccelerationStructureGeometryKHR.calloc(job.geometries.size(), stack);
                    var maxPrims = stack.callocInt(job.geometries.size());
                    buildRanges.put(brs);

                    long buildBufferSize = 0;
                    List<Integer> geometrySizes = new ArrayList<>(job.geometries.size());
                    List<VBuffer> hostGeometryBuffers = new ArrayList<>(job.geometries.size());

                    for (var geometry : job.geometries) {
                        //TODO: also need to store the buffer so it can be freed later (after blas build the vertex data can be freed as blas is self contained)
                        var buf = context.memory.createBuffer(geometry.geometry.getLength(),
                                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                                0, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
                        long ptr = buf.map();
                        MemoryUtil.memCopy(MemoryUtil.memAddress(geometry.geometry.getDirectBuffer()), ptr, geometry.geometry.getLength());
                        buf.unmap();
                        buffersToFree.add(buf);
                        hostGeometryBuffers.add(buf);
                        geometrySizes.add(geometry.geometry.getLength());

                        if (geometry.geometry.getLength() <= 0) {
                            throw new IllegalStateException("Geometry size <= 0");
                        }

                        buildBufferSize += geometry.geometry.getLength();
                        //After we copy it over, we can free the native buffer
                        geometry.geometry.free();
                    }

                    if (buildBufferSize <= 0) {
                        throw new IllegalStateException("Build buffer size <= 0");
                    }

                    var buildBuffer = context.memory.createBuffer(buildBufferSize,
                            VK_BUFFER_USAGE_TRANSFER_DST_BIT
                                    | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR
                                    | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                            0, 0);
                    buffersToFree.add(buildBuffer);
                    var buildBufferAddr = buildBuffer.deviceAddress();
                    long buildBufferOffset = 0;

                    for (int geoIdx = 0; geoIdx < job.geometries.size(); geoIdx++) {
                        var geometry = job.geometries.get(geoIdx);
                        //TODO: Fill in geometryInfo, maxPrims and buildRangeInfo
                        var geometryInfo = geometryInfos.get().sType$Default();
                        var br = brs.get();

                        VkDeviceOrHostAddressConstKHR indexData = SharedQuadVkIndexBuffer.getIndexBuffer(context, geometry.quadCount);
                        int indexType = SharedQuadVkIndexBuffer.TYPE;

                        VkDeviceOrHostAddressConstKHR vertexData = VkDeviceOrHostAddressConstKHR.calloc(stack).deviceAddress(buildBufferAddr + buildBufferOffset);
                        int vertexFormat = VK_FORMAT_R16G16B16_SFLOAT;//VK_FORMAT_R32G32B32_SFLOAT;
                        int vertexStride = 2*3;

                        geometryInfo.geometry(VkAccelerationStructureGeometryDataKHR.calloc(stack)
                                .triangles(VkAccelerationStructureGeometryTrianglesDataKHR.calloc(stack)
                                        .sType$Default()

                                        .vertexData(vertexData)
                                        .vertexFormat(vertexFormat)
                                        .vertexStride(vertexStride)
                                        .maxVertex(geometry.quadCount * 4)

                                        .indexData(indexData)
                                        .indexType(indexType)))
                                .geometryType(VK_GEOMETRY_TYPE_TRIANGLES_KHR)
                                .flags(geometry.geometryFlags);//TODO: ADD VkGeometryFlagsKHR VK_GEOMETRY_OPAQUE_BIT_KHR

                        maxPrims.put(geometry.quadCount * 2);
                        br.primitiveCount(geometry.quadCount * 2);
                        //maxPrims.put(2);
                        //br.primitiveCount(2);

                        var bufferCopy = VkBufferCopy.calloc(1, stack);
                        bufferCopy.get(0).srcOffset(0)
                                .dstOffset(buildBufferOffset)
                                .size(geometrySizes.get(geoIdx));
                        vkCmdCopyBuffer(uploadBuildCmd.buffer, hostGeometryBuffers.get(geoIdx).buffer(), buildBuffer.buffer(), bufferCopy);

                        buildBufferOffset += geometrySizes.get(geoIdx);
                    }

                    var bufferBarrier = VkBufferMemoryBarrier.calloc(1, stack);
                    bufferBarrier.get(0).sType$Default().buffer(buildBuffer.buffer())
                            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR).size(buildBufferSize);
                    vkCmdPipelineBarrier(uploadBuildCmd.buffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                            VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, 0, null, bufferBarrier, null);

                    geometryInfos.rewind();
                    maxPrims.rewind();

                    var bi = buildInfos.get()
                            .sType$Default()
                            .type(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR)
                            .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR | VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_COMPACTION_BIT_KHR)
                            .pGeometries(geometryInfos)
                            .geometryCount(job.geometries.size());

                    VkAccelerationStructureBuildSizesInfoKHR buildSizesInfo = VkAccelerationStructureBuildSizesInfoKHR
                            .calloc(stack)
                            .sType$Default();

                    vkGetAccelerationStructureBuildSizesKHR(
                            context.device,
                            VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                            bi,
                            maxPrims,
                            buildSizesInfo);


                    var structure = context.memory.createAcceleration(buildSizesInfo.accelerationStructureSize(), 256,
                            VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR, VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR);

                    var scratch = context.memory.createBuffer(buildSizesInfo.buildScratchSize(),
                            VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 256, 0);

                    bi.scratchData(VkDeviceOrHostAddressKHR.calloc(stack).deviceAddress(scratch.deviceAddress()));
                    bi.dstAccelerationStructure(structure.structure);

                    pAccelerationStructures.put(structure.structure);

                    accelerationStructures[i] = structure;
                    scratchBuffers[i] = scratch;
                }

                buildInfos.rewind();
                buildRanges.rewind();
                pAccelerationStructures.rewind();

                VSemaphore link = context.sync.createBinarySemaphore();
                {
                    vkCmdBuildAccelerationStructuresKHR(uploadBuildCmd.buffer, buildInfos, buildRanges);

                    //TODO: should probably do memory barrier to read access
                    vkCmdPipelineBarrier(uploadBuildCmd.buffer, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, 0, VkMemoryBarrier.calloc(1)
                            .sType$Default()
                            .srcAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR)
                            .dstAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR), null, null);

                    queryPool.reset(uploadBuildCmd, 0, jobs.size());
                    vkCmdWriteAccelerationStructuresPropertiesKHR(
                            uploadBuildCmd.buffer,
                            pAccelerationStructures,
                            VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_KHR,
                            queryPool.pool,
                            0);

                    vkCmdPipelineBarrier(uploadBuildCmd.buffer, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, null, null, null);

                    uploadBuildCmd.end();

                    VFence buildFence = context.sync.createFence();
                    context.cmd.submit(asyncQueue, new VCmdBuff[]{uploadBuildCmd}, new VSemaphore[0], new int[0],
                            new VSemaphore[]{link},
                            buildFence);

                    //vkDeviceWaitIdle(context.device);
                    {
                        //We need to wait for the fence which signals that the build has finished and we can query the result
                        // TODO: find a cleaner way to wait on the query results (tho i think waiting on a fence is realistically the easiest thing to do)
                        vkWaitForFences(context.device, buildFence.address(), true, -1);

                        //FIXME: this is extream jank, we can clean up after this point because we know the fence has passed, this is so ugly tho

                        for (var sb : scratchBuffers) {
                            sb.free();
                        }
                        for (var b : buffersToFree) {
                            b.free();
                        }

                        sinlgeUsePool.releaseNow(uploadBuildCmd);

                        //We can destroy the fence here since we know its passed
                        buildFence.free();
                    }
                }

                long[] compactedSizes = queryPool.getResultsLong(jobs.size());
                VSemaphore awaitSemaphore = context.sync.createBinarySemaphore();

                VAccelerationStructure[] compactedAS = new VAccelerationStructure[jobs.size()];

                List<BLASBuildResult> results = new ArrayList<>();

                //vkDeviceWaitIdle(context.device);

                //---------------------
                {
                    var cmd = sinlgeUsePool.createCommandBuffer();
                    cmd.begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

                    //Dont need a memory barrier cause submit ensures cache flushing already

                    for (int idx = 0; idx < compactedSizes.length; idx++) {
                        var as = context.memory.createAcceleration(compactedSizes[idx], 256,
                                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR, VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR);

                        vkCmdCopyAccelerationStructureKHR(cmd.buffer, VkCopyAccelerationStructureInfoKHR.calloc(stack).sType$Default()
                                .src(accelerationStructures[idx].structure)
                                .dst(as.structure)
                                .mode(VK_COPY_ACCELERATION_STRUCTURE_MODE_COMPACT_KHR));

                        compactedAS[idx] = as;
                        var job = jobs.get(idx);
                        results.add(new BLASBuildResult(as, job.data));
                    }

                    vkCmdPipelineBarrier(cmd.buffer, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, null, null, null);

                    VFence fence = context.sync.createFence();

                    cmd.end();
                    context.cmd.submit(asyncQueue, new VCmdBuff[]{cmd},
                            new VSemaphore[]{link},
                            new int[]{VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR},
                            new VSemaphore[]{awaitSemaphore},
                            fence);

                    context.sync.addCallback(fence, () -> {
                        for (var as : accelerationStructures) {
                            as.free();
                        }

                        cmd.enqueueFree();
                        fence.free();
                        link.free();
                    });
                }

                //Submit to callback, linking the build semaphore so that we dont stall the queue more than we need
                resultConsumer.accept(new BLASBatchResult(results, awaitSemaphore));

                //TODO: FIXME, so there is an issue in that the query pool needs to be represented per thing
                // since the cpu can run ahead of the gpu, need multiple query pools until we know the gpu is finished with it
                // using a fence, OR FOR THREAD ONLY we can assume that we allow one batch per queue
                //that is, at the end (here) we do a vkWaitForFences on the final fence, _then_ we can release all the temporary
                // resources that we know weve used, TODO: I THINK I FIXED THIS BY PUTTING A FENCE SYNC between stage 1 and 2

                //NOTE: THIS IS ACTUALLY WRONG, since we need to block on the query result in order to do the second part
                // it doesnt matter about needing multiple query pools

                //vkDeviceWaitIdle(context.device);
            }
        }
    }



    private VBuffer uploadTerrainGeometry(BuiltSectionMeshParts meshParts) {
        var buff = context.memory.createSharedBuffer(meshParts.getVertexData().getLength(),
                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        int glHolder = glCreateBuffers();
        nglNamedBufferData(glHolder, meshParts.getVertexData().getLength(), MemoryUtil.memAddress(meshParts.getVertexData().getDirectBuffer()), GL_STREAM_COPY);
        glCopyNamedBufferSubData(glHolder, buff.glId, 0, 0, meshParts.getVertexData().getLength());
        glDeleteBuffers(glHolder);

        return buff;
    }

    //Enqueues jobs of section blas builds
    public void enqueue(List<ChunkBuildOutput> batch) {
        List<BLASBuildJob> jobs = new ArrayList<>(batch.size());
        for (ChunkBuildOutput cbr : batch) {
            var acbr = ((IAccelerationBuildResult)cbr).getAccelerationGeometryData();
            if (acbr == null)
                continue;
            List<BLASTriangleData> buildData = new ArrayList<>();
            List<VBuffer> geometryBuffers = new ArrayList<>();
            for (var entry : acbr.entrySet()) {
                //TODO: dont hardcode the stride size
                int flag = entry.getKey() == DefaultTerrainRenderPasses.SOLID?VK_GEOMETRY_OPAQUE_BIT_KHR:0;
                buildData.add(new BLASTriangleData(entry.getValue().quadCount(), entry.getValue().data(), flag));

                var geometry = cbr.getMesh(entry.getKey());
                if (geometry.getVertexData().getLength() == 0) {
                    throw new IllegalStateException();
                }
                geometryBuffers.add(uploadTerrainGeometry(geometry));
            }

            if (buildData.size() > 0) {
                jobs.add(new BLASBuildJob(buildData, new JobPassThroughData(cbr.render, cbr.buildTime, geometryBuffers)));
            }
        }

        if (jobs.isEmpty()) {
            return;//No jobs to do
        }
        batchedJobs.add(jobs);
        awaitingJobBatchess.release();
    }
}
