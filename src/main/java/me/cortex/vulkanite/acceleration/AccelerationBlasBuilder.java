package me.cortex.vulkanite.acceleration;


//Multithreaded acceleration manager, builds blas's in a seperate queue,
// then memory copies over to main, while doing compaction

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.VQueryPool;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class AccelerationBlasBuilder {
    private final VContext context;
    private record BLASTriangleData(int quadCount, NativeBuffer geometry) {}
    private record BLASBuildJob(List<BLASTriangleData> geometries) {}
    public record BLASBuildResult(VAccelerationStructure structure) {}
    public record BLASBatchResult(List<BLASBuildResult> results, VSemaphore semaphore) { }
    private final Thread worker;
    private final int asyncQueue;
    private final Consumer<BLASBatchResult> resultConsumer;

    private final VQueryPool queryPool;

    //TODO: maybe move to an executor type system
    private final Semaphore awaitingJobs = new Semaphore(0);//Note: this is done to avoid spin locking on the job consumer
    private final ConcurrentLinkedDeque<List<BLASBuildJob>> batchedJobs = new ConcurrentLinkedDeque<>();

    public AccelerationBlasBuilder(VContext context, int asyncQueue, Consumer<BLASBatchResult> resultConsumer) {
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
        List<BLASBuildJob> jobs = new ArrayList<>();
        while (true) {
            {
                jobs.clear();
                try {
                    awaitingJobs.acquire();
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
                    //This updates the semaphore to be accurate, should be able to grab exactly i permits
                    try {
                        awaitingJobs.acquire(i);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            //Jobs are batched and built on the async vulkan queue then block synchronized with fence
            // which then results in compaction and dispatch to consumer

            //TODO: clean up this spaghetti shithole
            try (var stack = stackPush()) {
                var buildInfos = VkAccelerationStructureBuildGeometryInfoKHR.calloc(jobs.size(), stack);
                PointerBuffer buildRanges = stack.mallocPointer(jobs.size());
                LongBuffer pAccelerationStructures = stack.mallocLong(jobs.size());

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

                        VkDeviceOrHostAddressConstKHR indexData = SharedQuadVkIndexBuffer.getIndexBuffer(geometry.quadCount);
                        int indexType = SharedQuadVkIndexBuffer.TYPE;

                        VkDeviceOrHostAddressConstKHR vertexData = null;//TODO: FIXME
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

                    var scratch = context.memory.createBuffer(buildSizesInfo.buildScratchSize(), 256,//TODO: dont hardcode alignment
                            VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

                    bi.scratchData(VkDeviceOrHostAddressKHR.calloc(stack).deviceAddress(scratch.deviceAddress()));

                    pAccelerationStructures.put(structure.structure);

                    accelerationStructures[i] = structure;
                    scratchBuffers[i] = scratch;
                }

                buildInfos.rewind();
                buildRanges.rewind();

                VSemaphore link = null;
                {
                    var cmd = context.cmd.singleTimeCommand();
                    //TODO: barrier, maybe not needed

                    vkCmdBuildAccelerationStructuresKHR(cmd.buffer, buildInfos, buildRanges);

                    //TODO: barrier

                    queryPool.reset(cmd, 0, jobs.size());
                    vkCmdWriteAccelerationStructuresPropertiesKHR(
                            cmd.buffer,
                            pAccelerationStructures,
                            VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_KHR,
                            queryPool.pool,
                            0);

                    //TODO: barrier to bottom

                    //TODO: create semaphore and barrier

                    //TODO: submit cmd
                    context.cmd.submit(asyncQueue, new VCmdBuff[]{cmd}, new VSemaphore[0], new int[0], new VSemaphore[]{link}, null);
                }

                //TODO: wait for query results, check if can clean resources with the fence
                long[] compactedSizes = queryPool.getResultsLong(jobs.size());

                VSemaphore awaitSemaphore = null;

                //TODO: sync the 2 submisions with a semahore
                //---------------------
                {
                    var cmd = context.cmd.singleTimeCommand();

                    //TODO: BARRIER?

                    //TODO: create new compacted acceleration structures (from size of query)

                    //TODO: copy old acceleration structure to new accerleration structure

                    //TODO: Barrier

                    //TODO: Add fence to cleanup resources with (uncompacted memory buffer)

                    //TODO: submit cmd with semaphore
                    context.cmd.submit(asyncQueue, new VCmdBuff[]{cmd}, new VSemaphore[]{link}, new int[]{VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR}, new VSemaphore[]{awaitSemaphore}, null);
                }

                //Submit to callback, linking the build semaphore so that we dont stall the queue more than we need
                resultConsumer.accept(new BLASBatchResult(null, awaitSemaphore));
            }
        }
    }

    //Enqueues a section blas build
    public void enqueue() {

    }
}
