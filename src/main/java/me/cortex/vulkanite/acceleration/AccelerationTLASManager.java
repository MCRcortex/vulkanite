package me.cortex.vulkanite.acceleration;

//TLAS manager, ingests blas build requests and manages builds and syncs the tlas

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.cmd.VCommandPool;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.sync.VFence;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;

public class AccelerationTLASManager {
    private final TLASSectionManager buildDataManager = new TLASSectionManager();
    private final VContext context;
    private final int queue;
    private final VCommandPool singleUsePool;

    private VAccelerationStructure currentTLAS;

    public AccelerationTLASManager(VContext context, int queue) {
        this.context = context;
        this.queue = queue;
        this.singleUsePool = context.cmd.createSingleUsePool();
    }

    //Returns a sync semaphore to chain in the next command submit
    public void updateSections(List<AccelerationBlasBuilder.BLASBuildResult> results) {
        for (var result : results) {

            //boolean canAcceptResult = (!result.section().isDisposed()) && result.time() >= result.section().lastAcceptedBuildTime;

            buildDataManager.update(result);
        }
    }


    //TODO: cleanup, this is very messy
    public VSemaphore buildTLAS(VSemaphore renderLink, VSemaphore[] blocking) {
        singleUsePool.doReleases();

        //NOTE: renderLink is required to ensure that we are not overriding memory that is actively being used for frames
        // should have a VK_PIPELINE_STAGE_TRANSFER_BIT blocking bit
        try (var stack = stackPush()) {
            //The way the tlas build works is that terrain data is split up into regions, each region is its own geometry input
            // this is done for performance reasons when updating (adding/removing) sections

            //This would also be where other geometries (such as entities) get added to the tlas TODO: implement entities

            //TODO: look into doing an update instead of a full tlas rebuild so instead just update the tlas to a new
            // acceleration structure!!!


            //The reason its done like this is so that entities and stuff can be easily added to the tlas manager
            VkAccelerationStructureGeometryKHR.Buffer geometries = VkAccelerationStructureGeometryKHR.calloc(1, stack);
            int[] instanceCounts = new int[1];

            VFence fence = context.sync.createFence();


            var cmd = singleUsePool.createCommandBuffer();
            cmd.begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT );

            {
                //TODO: need to sync with respect to updates from gpu memory updates from TLASBuildDataManager
                // OR SOMETHING CAUSE WITH MULTIPLE FRAMES GOING AT ONCE the gpu state of TLASBuildDataManager needs to be synced with
                // the current build phase, and the gpu side needs to be updated accoringly and synced correctly

                vkCmdPipelineBarrier(cmd.buffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                        VkMemoryBarrier.calloc(1, stack)
                                .sType$Default()
                                .srcAccessMask(0)
                                .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT | VK_ACCESS_TRANSFER_READ_BIT),
                        null, null);

                buildDataManager.setGeometryUpdateMemory(cmd, fence, geometries.get(0));
                instanceCounts[0] = buildDataManager.sectionCount();

                vkCmdPipelineBarrier(cmd.buffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, 0,
                        VkMemoryBarrier.calloc(1, stack)
                                .sType$Default()
                                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT),
                        null, null);
            }



            var buildInfo = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack)
                    .sType$Default()
                    .mode(VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR)//TODO: explore using VK_BUILD_ACCELERATION_STRUCTURE_MODE_UPDATE_KHR to speedup build times
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR)
                    .pGeometries(geometries)
                    .geometryCount(geometries.capacity());

            VkAccelerationStructureBuildSizesInfoKHR buildSizesInfo = VkAccelerationStructureBuildSizesInfoKHR
                    .calloc(stack)
                    .sType$Default();

            vkGetAccelerationStructureBuildSizesKHR(
                    context.device,
                    VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                    buildInfo.get(0),//The reason its a buffer is cause of pain and that vkCmdBuildAccelerationStructuresKHR requires a buffer of VkAccelerationStructureBuildGeometryInfoKHR
                    stack.ints(instanceCounts),
                    buildSizesInfo);

            VAccelerationStructure tlas = context.memory.createAcceleration(buildSizesInfo.accelerationStructureSize(), 256,
                    0, VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR);

            //TODO: instead of making a new scratch buffer, try to reuse
            // ACTUALLY wait since we doing the on fence free thing, we dont have to worry about that and it should
            // get automatically freed since we using vma dont have to worry about performance _too_ much i think
            VBuffer scratchBuffer = context.memory.createBuffer(buildSizesInfo.buildScratchSize(),
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 256);

            buildInfo.dstAccelerationStructure(tlas.structure)
                    .scratchData(VkDeviceOrHostAddressKHR.calloc(stack)
                            .deviceAddress(scratchBuffer.deviceAddress()));

            var buildRanges = VkAccelerationStructureBuildRangeInfoKHR.calloc(instanceCounts.length, stack);
            for (int count : instanceCounts) {
                buildRanges.get().primitiveCount(count);
            }
            buildRanges.rewind();

            vkCmdBuildAccelerationStructuresKHR(cmd.buffer,
                    buildInfo,
                    stack.pointers(buildRanges));

            VSemaphore chain = context.sync.createBinarySemaphore();

            cmd.end();
            int[] waitingStage = new int[blocking.length];
            Arrays.fill(waitingStage, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR | VK_PIPELINE_STAGE_TRANSFER_BIT);
            context.cmd.submit(queue, new VCmdBuff[]{cmd}, blocking, waitingStage, new VSemaphore[]{chain}, fence);

            VAccelerationStructure oldTLAS = currentTLAS;
            currentTLAS = tlas;
            context.sync.addCallback(fence, () -> {
                scratchBuffer.free();
                oldTLAS.free();
                cmd.enqueueFree();
            });

            return chain;
        }
    }

    //Manages entries in the VkAccelerationStructureInstanceKHR buffer, ment to reuse as much as possible and be very efficient
    private class TLASGeometryManager {
        //Have a global buffer for VkAccelerationStructureInstanceKHR, then use
        // VkAccelerationStructureGeometryInstancesDataKHR.arrayOfPointers
        //Use LibCString.memmove to ensure streaming data is compact
        //  Stream this to the gpu per frame (not ideal tbh, could implement a cache of some kind)

        //Needs a gpu buffer for the instance data, this can be reused
        //private VkAccelerationStructureInstanceKHR.Buffer buffer;

        private VBuffer instanceData;
        private long pointers;
        private BitSet free;
        private int[] instance2pointer;
        private int[] pointer2instance;
        private int count;




        //TODO: just do the whole instance2pointer map idea thing directly on the VkAccelerationStructureInstanceKHR buffer
        public void setGeometryUpdateMemory(VCmdBuff cmd, VFence fence, VkAccelerationStructureGeometryKHR struct) {
            //TODO: should probably cache the pointerData, since it should be pretty constant and only change if new geometry was added or removed (changing geometry doesnt cause update)
            VBuffer pointerData = context.memory.createBufferGlobal(count * 8L, VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VK_MEMORY_HEAP_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
            long ptr = pointerData.map();
            MemoryUtil.memCopy(this.pointers, ptr, count * 8L);
            pointerData.unmap();
            pointerData.flush();




            context.sync.addCallback(fence, () -> {
                pointerData.free();

            });

            struct.sType$Default()
                    .geometryType(VK_GEOMETRY_TYPE_INSTANCES_KHR)
                    .flags(0);

            struct.geometry()
                    .instances()
                    .sType$Default()
                    .arrayOfPointers(true);

            struct.geometry()
                    .instances()
                    .data()
                    .deviceAddress(pointerData.deviceAddress());
        }

        public int sectionCount() {
            return count;
        }

        protected int alloc() {
            int id = free.nextSetBit(0);
            free.clear(id);

            //Update the map
            instance2pointer[id] = count;
            instance2pointer[count] = id;

            //Set the pointer if the instance data
            MemoryUtil.memPutAddress(pointers + count*8L, instanceData.deviceAddress() + (long) id * VkAccelerationStructureInstanceKHR.SIZEOF);

            //Increment the count
            count++;

            return id;
        }

        protected void free(int id) {
            free.set(id);

            if (instance2pointer[id] == count) {
                //We are at the end of the pointer list, so just decrement and be done
                count--;
                instance2pointer[id] = -1;
                pointer2instance[count] = -1;
            } else {
                //TODO: CHECK THIS IS CORRECT

                //We need to remove the pointer, and fill it in with the last element in the pointer array, updating the mapping of the moved
                int ptrId = instance2pointer[id];
                instance2pointer[id] = -1;
                count--;

                pointer2instance[ptrId] = instance2pointer[count];
                MemoryUtil.memPutAddress(pointers + ptrId * 8L, MemoryUtil.memGetAddress(pointers + count * 8L));
                instance2pointer[pointer2instance[count]] = ptrId;
            }
        }

        protected void update(int id, VkAccelerationStructureInstanceKHR data) {

        }
    }

    private final class TLASSectionManager extends TLASGeometryManager {
        //TODO: mixinto RenderSection and add a reference to a holder for us, its much faster than a hashmap
        public void update(AccelerationBlasBuilder.BLASBuildResult result) {

        }

        public void remove(RenderSection section) {

        }
    }
}
