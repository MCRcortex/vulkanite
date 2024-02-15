package me.cortex.vulkanite.acceleration;


//Multithreaded acceleration manager, builds blas's in a seperate queue,
// then memory copies over to main, while doing compaction

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IAccelerationBuildResult;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.cmd.VCommandPool;
import me.cortex.vulkanite.lib.descriptors.DescriptorSetLayoutBuilder;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSetLayout;
import me.cortex.vulkanite.lib.memory.PoolLinearAllocator;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.VQueryPool;
import me.cortex.vulkanite.lib.other.sync.VFence;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.cortex.vulkanite.lib.pipeline.ComputePipelineBuilder;
import me.cortex.vulkanite.lib.pipeline.VComputePipeline;
import me.cortex.vulkanite.lib.shader.ShaderCompiler;
import me.cortex.vulkanite.lib.shader.ShaderModule;
import me.cortex.vulkanite.lib.shader.VShader;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
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

import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.*;

public class AccelerationBlasBuilder {
    private final VContext context;
    private record BLASTriangleData(int quadCount, int geometryFlags) {}
    private record BLASBuildJob(List<BLASTriangleData> geometries, JobPassThroughData data) {}
    public record BLASBuildResult(VRef<VAccelerationStructure> structure, JobPassThroughData data) {}
    public record BLASBatchResult(List<BLASBuildResult> results, long execution) { }

    private final int asyncQueue;
    private final Consumer<BLASBatchResult> resultConsumer;

    private final VRef<VQueryPool> queryPool;

    //TODO: maybe move to an executor type system
    private final Semaphore awaitingJobBatchess = new Semaphore(0);//Note: this is done to avoid spin locking on the job consumer
    private final ConcurrentLinkedDeque<List<BLASBuildJob>> batchedJobs = new ConcurrentLinkedDeque<>();

    private final VRef<VComputePipeline> gpuVertexDecodePipeline;

    public int getAsyncQueue() {
        return asyncQueue;
    }

    public AccelerationBlasBuilder(VContext context, int asyncQueue, Consumer<BLASBatchResult> resultConsumer) {
        this.queryPool = VQueryPool.create(context.device, 10000, VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_KHR);
        this.context = context;
        this.asyncQueue = asyncQueue;
        this.resultConsumer = resultConsumer;

        var decodeShader = VShader.compileLoad(context, """
                        #version 460
                        #extension GL_EXT_buffer_reference : require
                        #extension GL_EXT_shader_8bit_storage : require
                        #extension GL_EXT_shader_explicit_arithmetic_types : require
                        #extension GL_EXT_shader_16bit_storage : require
                                        
                        layout (local_size_x = 256, local_size_y = 1, local_size_z = 1) in;
                                        
                        struct InputVertex {
                            u16vec4 position;
                            u8vec4 color;
                            u16vec2 blockTexture;
                            u16vec2 lightTexture;
                            u16vec2 midTexCoord;
                            i8vec4 tangent;
                            i8vec3 normal;
                            int8_t padA__;
                            i16vec2 blockId;
                            i8vec3 midBlock;
                            int8_t padB__;
                        };
                        
                        layout(buffer_reference, std430) buffer InputVertices {
                            InputVertex vertices[];
                        };
                        
                        layout(buffer_reference, std430) buffer OutputVertices {
                            float vertices[];
                        };
                        
                        layout(push_constant) uniform PushConstants {
                            uint64_t nVertices;
                            uint64_t inAddr;
                            uint64_t outAddr;
                        };
                                        
                        void main() {
                            uint32_t idx = gl_GlobalInvocationID.x;
                            uint32_t gridSize = gl_NumWorkGroups.x * gl_WorkGroupSize.x;
                            InputVertices inputs = InputVertices(inAddr);
                            OutputVertices outputs = OutputVertices(outAddr);
                            for (idx; idx < uint32_t(nVertices); idx += gridSize) {
                                vec3 position = vec3(inputs.vertices[idx].position.xyz) * (32.0 / 65536.0) - 8.0;
                                outputs.vertices[idx * 3 + 0] = position.x;
                                outputs.vertices[idx * 3 + 1] = position.y;
                                outputs.vertices[idx * 3 + 2] = position.z;
                            }
                        }
                        """,
                VK_SHADER_STAGE_COMPUTE_BIT);

        var decodePipeBuilder = new ComputePipelineBuilder();
        decodePipeBuilder.addPushConstantRange(8 * 3, 0);
        decodePipeBuilder.set(decodeShader.get().named());
        gpuVertexDecodePipeline = decodePipeBuilder.build(context);

        Thread worker = new Thread(this::run);
        worker.setName("Acceleration blas worker");
        worker.start();
    }

    //The acceleration manager blas builder runs in batches/groups
    private void run() {
        MemoryStack bigStack = MemoryStack.create(20_000_000);

        var buildBufferAllocator = new PoolLinearAllocator(context, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR
                | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, 0x200_0000L, 16);
        var scratchAllocator = new PoolLinearAllocator(context, VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                0x200_0000L, 256);
        var initialASBufferAllocator = new PoolLinearAllocator(context, VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR,
                0x200_0000L, 256);

        List<BLASBuildJob> jobs = new ArrayList<>();
        Deque<Long> priorExecutions = new ArrayDeque<>(3);
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
                while (!this.batchedJobs.isEmpty() && jobs.size() < 32) {
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

            var sinlgeUsePoolWorker = context.cmd.getSingleUsePool();

            //Jobs are batched and built on the async vulkan queue then block synchronized with fence
            // which then results in compaction and dispatch to consumer

            //TODO: clean up this spaghetti shithole
            try (var stack = bigStack.push()) {
                var buildInfos = VkAccelerationStructureBuildGeometryInfoKHR.calloc(jobs.size(), stack);
                PointerBuffer buildRanges = stack.mallocPointer(jobs.size());
                LongBuffer pAccelerationStructures = stack.mallocLong(jobs.size());

                var accelerationStructures = new ArrayList<VRef<VAccelerationStructure>>(jobs.size());

                var uploadBuildCmdRef = sinlgeUsePoolWorker.createCommandBuffer();
                var uploadBuildCmd = uploadBuildCmdRef.get();

                //Fill in the buildInfo and buildRanges
                int i = -1;
                uploadBuildCmd.bindCompute(gpuVertexDecodePipeline);

                for (var job : jobs) {
                    i++;
                    var brs = VkAccelerationStructureBuildRangeInfoKHR.calloc(job.geometries.size(), stack);
                    var geometryInfos = VkAccelerationStructureGeometryKHR.calloc(job.geometries.size(), stack);
                    var maxPrims = stack.callocInt(job.geometries.size());
                    buildRanges.put(brs);

                    long vertexStride = 4 * 3;

                    for (int geoIdx = 0; geoIdx < job.geometries.size(); geoIdx++) {
                        var geometry = job.geometries.get(geoIdx);
                        //TODO: Fill in geometryInfo, maxPrims and buildRangeInfo
                        var geometryInfo = geometryInfos.get().sType$Default();
                        var br = brs.get();

                        long buildBufferSize = geometry.quadCount * 4L * vertexStride;
                        var buildBuffer = buildBufferAllocator.allocate(buildBufferSize);

                        uploadBuildCmd.encodeBufferBarrier(buildBuffer.buffer(), buildBuffer.offset(), buildBuffer.size(), VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);

                        // We know the geometry data has been uploaded
                        // 0: n_vertices
                        // 1: inAddr
                        // 2: outAddr
                        var pushConstant = new long[3];
                        var geometryInputBuffer = job.data.geometryBuffers().get(geoIdx);
                        pushConstant[0] = geometry.quadCount * 4L;
                        pushConstant[1] = geometryInputBuffer.get().deviceAddress();
                        pushConstant[2] = buildBuffer.deviceAddress();
                        if (pushConstant[1] == 0) {
                            throw new IllegalStateException("Geometry input buffer address is 0");
                        }
                        if (pushConstant[2] == 0) {
                            throw new IllegalStateException("Build buffer address is 0");
                        }
                        uploadBuildCmd.pushConstants(0, pushConstant);
                        uploadBuildCmd.encodeBufferBarrier(geometryInputBuffer, 0, VK_WHOLE_SIZE, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
                        uploadBuildCmd.dispatch(Math.min((geometry.quadCount * 4 + 255) / 256, 256), 1, 1);

                        uploadBuildCmd.encodeBufferBarrier(buildBuffer.buffer(), buildBuffer.offset(), buildBuffer.size(), VK_PIPELINE_STAGE_TRANSFER_BIT,
                                VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR);

                        var indexBuffer = SharedQuadVkIndexBuffer.getIndexBuffer(context,
                                uploadBuildCmd,
                                Integer.max(geometry.quadCount, 30000));
                        VkDeviceOrHostAddressConstKHR indexData = indexBuffer.get().deviceAddressConst();
                        int indexType = SharedQuadVkIndexBuffer.TYPE;

                        uploadBuildCmd.addBufferRef(indexBuffer);

                        VkDeviceOrHostAddressConstKHR vertexData = VkDeviceOrHostAddressConstKHR.calloc(stack)
                                .deviceAddress(buildBuffer.deviceAddress());
                        int vertexFormat = VK_FORMAT_R32G32B32_SFLOAT;

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
                                .flags(geometry.geometryFlags);

                        maxPrims.put(geometry.quadCount * 2);
                        br.primitiveCount(geometry.quadCount * 2);
                        //maxPrims.put(2);
                        //br.primitiveCount(2);
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


                    var backingBuffer = initialASBufferAllocator.allocate(buildSizesInfo.accelerationStructureSize());
                    var structure = context.memory.createAcceleration(backingBuffer.buffer(), backingBuffer.offset(), backingBuffer.size(), VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR);

                    var scratch = scratchAllocator.allocate(buildSizesInfo.buildScratchSize());
                    uploadBuildCmd.addBufferRef(scratch.buffer());
                    bi.scratchData(VkDeviceOrHostAddressKHR.calloc(stack).deviceAddress(scratch.deviceAddress()));
                    bi.dstAccelerationStructure(structure.get().structure);

                    pAccelerationStructures.put(structure.get().structure);

                    accelerationStructures.add(structure);
                }

                buildInfos.rewind();
                buildRanges.rewind();
                pAccelerationStructures.rewind();

                {
                    vkCmdBuildAccelerationStructuresKHR(uploadBuildCmd.buffer(), buildInfos, buildRanges);

                    //TODO: should probably do memory barrier to read access
                    uploadBuildCmd.encodeMemoryBarrier();

                    uploadBuildCmd.resetQueryPool(queryPool, 0, jobs.size());
                    vkCmdWriteAccelerationStructuresPropertiesKHR(
                            uploadBuildCmd.buffer(),
                            pAccelerationStructures,
                            VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_KHR,
                            queryPool.get().pool,
                            0);

                    long buildExecution = context.cmd.submit(asyncQueue, uploadBuildCmdRef);

                    // Waiting on a semaphore is realistically the easiest thing to do rn
                    context.cmd.hostWaitForExecution(asyncQueue, buildExecution);
                    uploadBuildCmdRef.close();
                }

                long[] compactedSizes = queryPool.get().getResultsLong(jobs.size());

                List<BLASBuildResult> results = new ArrayList<>();

                //vkDeviceWaitIdle(context.device);

                //---------------------
                long blasExecution = 0;
                {
                    var cmdRef = sinlgeUsePoolWorker.createCommandBuffer();

                    // Dont need a memory barrier cause submit ensures cache flushing already
                    for (int idx = 0; idx < compactedSizes.length; idx++) {
                        var compact_as = context.memory.createAcceleration(compactedSizes[idx], 256,
                                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR, VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR);
                        var fat_as = accelerationStructures.get(idx);

                        vkCmdCopyAccelerationStructureKHR(cmdRef.get().buffer(), VkCopyAccelerationStructureInfoKHR.calloc(stack).sType$Default()
                                .src(fat_as.get().structure)
                                .dst(compact_as.get().structure)
                                .mode(VK_COPY_ACCELERATION_STRUCTURE_MODE_COMPACT_KHR));

                        cmdRef.get().addAccelerationStructureRef(fat_as);
                        cmdRef.get().addAccelerationStructureRef(compact_as);
                        fat_as.close();

                        var job = jobs.get(idx);
                        results.add(new BLASBuildResult(compact_as, job.data));
                    }

                    blasExecution = context.cmd.submit(asyncQueue, cmdRef);
                    cmdRef.close();
                }

                accelerationStructures.forEach(VRef::close);

                resultConsumer.accept(new BLASBatchResult(results, blasExecution));

                priorExecutions.add(blasExecution);
                if (priorExecutions.size() > 3) {
                    long prior = priorExecutions.poll();
                    context.cmd.hostWaitForExecution(asyncQueue, prior);
                }
            }
        }
    }



    private VRef<VBuffer> uploadTerrainGeometry(BuiltSectionMeshParts meshParts, VCmdBuff cmd) {
        var buff = context.memory.createBuffer(meshParts.getVertexData().getLength(),
                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        buff.get().setDebugUtilsObjectName("Terrain geometry buffer");

        cmd.encodeDataUpload(context.memory, MemoryUtil.memAddress(meshParts.getVertexData().getDirectBuffer()), buff, 0, meshParts.getVertexData().getLength());

        return buff;
    }

    // Enqueues jobs of section blas builds
    // NOTE: This is on a different thread!
    public void enqueue(List<ChunkBuildOutput> batch) {
        var cmd = context.cmd.getSingleUsePool().createCommandBuffer();
        boolean hasJobs = false;

        List<BLASBuildJob> jobs = new ArrayList<>(batch.size());
        for (ChunkBuildOutput cbr : batch) {
            var acbr = ((IAccelerationBuildResult) cbr).getAccelerationGeometryData();
            if (acbr == null)
                continue;
            List<BLASTriangleData> buildData = new ArrayList<>();
            List<VRef<VBuffer>> geometryBuffers = new ArrayList<>();
            for (var entry : acbr.entrySet()) {
                int flag = entry.getKey() == DefaultTerrainRenderPasses.SOLID ? VK_GEOMETRY_OPAQUE_BIT_KHR : 0;
                buildData.add(new BLASTriangleData(entry.getValue().quadCount(), flag));

                var geometry = cbr.getMesh(entry.getKey());
                if (geometry.getVertexData().getLength() == 0) {
                    throw new IllegalStateException();
                }

                if (!hasJobs) {
                    hasJobs = true;
                }

                geometryBuffers.add(uploadTerrainGeometry(geometry, cmd.get()));
            }

            if (buildData.size() > 0) {
                jobs.add(new BLASBuildJob(buildData,
                        new JobPassThroughData(cbr.render, cbr.buildTime, geometryBuffers)));
            }
        }

        if (hasJobs) {
            // TODO: Which queue should this be submitted to?
            // Should we move all uploads to the worker?
            context.cmd.submitOnceAndWait(1, cmd);
        }
        cmd.close();

        if (jobs.isEmpty()) {
            return; // No jobs to do
        }
        batchedJobs.add(jobs);
        awaitingJobBatchess.release();
    }
}
