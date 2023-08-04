package me.cortex.vulkanite.acceleration;


//Multithreaded acceleration manager, builds blas's in a seperate queue,
// then memory copies over to main, while doing compaction

import me.cortex.vulkanite.client.IAccelerationBuildResult;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.cmd.VCommandPool;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.VQueryPool;
import me.cortex.vulkanite.lib.other.sync.VFence;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
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
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;

public class AccelerationBlasBuilder {
    private final VContext context;
    private record BLASTriangleData(int quadCount, NativeBuffer geometry) {}
    private record BLASBuildJob(List<BLASTriangleData> geometries, RenderSection section, long time) {}
    public record BLASBuildResult(VAccelerationStructure structure, RenderSection section, long time) {}
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
        this.sinlgeUsePool = new VCommandPool(context.device, VK_COMMAND_POOL_CREATE_TRANSIENT_BIT);
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

                List<VBuffer> geometryBuffers = new ArrayList<>(jobs.size()*2);
                var scratchBuffers = new VBuffer[jobs.size()];
                var accelerationStructures = new VAccelerationStructure[jobs.size()];

                //Fill in the buildInfo and buildRanges
                int i = -1;
                for (var job : jobs) {
                    i++;
                    var brs = VkAccelerationStructureBuildRangeInfoKHR.calloc(job.geometries.size(), stack);
                    var geometryInfos = VkAccelerationStructureGeometryKHR.calloc(job.geometries.size(), stack);
                    var maxPrims = stack.callocInt(job.geometries.size());
                    buildRanges.put(brs);

                    for (var geometry : job.geometries) {
                        //TODO: Fill in geometryInfo, maxPrims and buildRangeInfo
                        var geometryInfo = geometryInfos.get().sType$Default();
                        var br = brs.get();
                        //TODO: upload all the geometry into a single vk buffer (by allocating it with VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT )
                        // mapping it to the cpu then doing vkFlushMappedMemoryRanges on the buffer

                        VkDeviceOrHostAddressConstKHR indexData = SharedQuadVkIndexBuffer.getIndexBuffer(context, geometry.quadCount);
                        int indexType = SharedQuadVkIndexBuffer.TYPE;

                        //TODO: also need to store the buffer so it can be freed later (after blas build the vertex data can be freed as blas is self contained)
                        var buf = context.memory.createBufferGlobal(geometry.geometry.getLength(), VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
                        long ptr = buf.map();
                        MemoryUtil.memCopy(MemoryUtil.memAddress(geometry.geometry.getDirectBuffer()), ptr, geometry.geometry.getLength());
                        buf.unmap();
                        buf.flush();
                        //After we copy it over, we can free the native buffer
                        geometry.geometry.free();
                        geometryBuffers.add(buf);


                        VkDeviceOrHostAddressConstKHR vertexData = VkDeviceOrHostAddressConstKHR.calloc(stack).deviceAddress(buf.deviceAddress());
                        int vertexFormat = VK_FORMAT_R32G32B32_SFLOAT;
                        int vertexStride = 4*3;

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
                                .flags();//TODO: ADD VkGeometryFlagsKHR VK_GEOMETRY_OPAQUE_BIT_KHR

                        maxPrims.put(geometry.quadCount * 2);
                        br.primitiveCount(geometry.quadCount * 2);
                    }

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


                    var structure = context.memory.createAcceleration(buildSizesInfo.accelerationStructureSize(), 256, //TODO: dont hardcode alignment
                            0, VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR);

                    var scratch = context.memory.createBuffer(buildSizesInfo.buildScratchSize(),
                            VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 256);//TODO: dont hardcode alignment

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
                    var cmd = sinlgeUsePool.createCommandBuffer();
                    cmd.begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

                    vkCmdBuildAccelerationStructuresKHR(cmd.buffer, buildInfos, buildRanges);

                    //TODO: should probably do memory barrier to read access
                    vkCmdPipelineBarrier(cmd.buffer, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, 0, VkMemoryBarrier.calloc(1).sType$Default()
                            .srcAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR)
                            .dstAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR), null, null);

                    queryPool.reset(cmd, 0, jobs.size());
                    vkCmdWriteAccelerationStructuresPropertiesKHR(
                            cmd.buffer,
                            pAccelerationStructures,
                            VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_KHR,
                            queryPool.pool,
                            0);


                    vkCmdPipelineBarrier(cmd.buffer, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, null, null, null);


                    VFence buildFence = context.sync.createFence();

                    cmd.end();
                    context.cmd.submit(asyncQueue, new VCmdBuff[]{cmd}, new VSemaphore[0], new int[0],
                            new VSemaphore[]{link},
                            buildFence);

                    {
                        //We need to wait for the fence which signals that the build has finished and we can query the result
                        // TODO: find a cleaner way to wait on the query results (tho i think waiting on a fence is realistically the easiest thing to do)
                        vkWaitForFences(context.device, buildFence.address(), true, -1);

                        //FIXME: this is extream jank, we can clean up after this point because we know the fence has passed, this is so ugly tho

                        for (var sb : scratchBuffers) {
                            sb.free();
                        }
                        for (var gb : geometryBuffers) {
                            gb.free();
                        }
                        //TODO: need to free the cmd buffer and fence AND THE SEMAPHORE
                        sinlgeUsePool.releaseNow(cmd);

                        //We can destroy the fence here since we know its passed
                        buildFence.free();

                        //TODO: maybe make it automatic in submit
                    }
                }

                long[] compactedSizes = queryPool.getResultsLong(jobs.size());
                VSemaphore awaitSemaphore = context.sync.createBinarySemaphore();

                VAccelerationStructure[] compactedAS = new VAccelerationStructure[jobs.size()];

                //---------------------
                {
                    var cmd = sinlgeUsePool.createCommandBuffer();
                    cmd.begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

                    //Dont need a memory barrier cause submit ensures cache flushing already

                    for (int idx = 0; idx < compactedSizes.length; idx++) {
                        var as = context.memory.createAcceleration(compactedSizes[idx], 256,//TODO: dont hardcode alignment
                                0, VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR);

                        vkCmdCopyAccelerationStructureKHR(cmd.buffer, VkCopyAccelerationStructureInfoKHR.calloc(stack).sType$Default()
                                .src(accelerationStructures[idx].structure)
                                .dst(as.structure)
                                .mode(VK_COPY_ACCELERATION_STRUCTURE_MODE_COMPACT_KHR));

                        compactedAS[idx] = as;
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
                resultConsumer.accept(new BLASBatchResult(null, awaitSemaphore));

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

    //Enqueues jobs of section blas builds
    public void enqueue(List<ChunkBuildResult> batch) {
        List<BLASBuildJob> jobs = new ArrayList<>(batch.size());
        for (ChunkBuildResult cbr : batch) {
            var acbr = ((IAccelerationBuildResult)cbr).getAccelerationGeometryData();
            if (acbr == null)
                continue;
            List<BLASTriangleData> buildData = new ArrayList<>();
            for (var passData : acbr.values()) {
                //TODO: dont hardcode the stride size
                buildData.add(new BLASTriangleData((passData.getLength()/12)/4, passData));
            }
            jobs.add(new BLASBuildJob(buildData, cbr.render, cbr.buildTime));
        }

        if (jobs.isEmpty()) {
            return;//No jobs to do
        }
        batchedJobs.add(jobs);
        awaitingJobBatchess.release();
    }
}
