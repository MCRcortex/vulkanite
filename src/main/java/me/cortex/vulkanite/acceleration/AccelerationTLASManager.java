package me.cortex.vulkanite.acceleration;

//TLAS manager, ingests blas build requests and manages builds and syncs the tlas

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.sync.VFence;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import org.lwjgl.PointerBuffer;
import org.lwjgl.vulkan.*;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class AccelerationTLASManager {
    private final TLASBuildDataManager buildDataManager = new TLASBuildDataManager();
    private final VContext context;
    private final int queue;
    private VAccelerationStructure currentTLAS;

    public AccelerationTLASManager(VContext context, int queue) {
        this.context = context;
        this.queue = queue;
    }

    //Returns a sync semaphore to chain in the next command submit
    public void updateSections(List<AccelerationBlasBuilder.BLASBuildResult> results) {

    }

    public VSemaphore buildTLAS(VSemaphore renderLink, VSemaphore[] blocking) {
        //NOTE: renderLink is required to ensure that we are not overriding memory that is actively being used for frames
        // should have a VK_PIPELINE_STAGE_TRANSFER_BIT blocking bit
        try (var stack = stackPush()) {
            //The way the tlas build works is that terrain data is split up into regions, each region is its own geometry input
            // this is done for performance reasons when updating (adding/removing) sections

            //This would also be where other geometries (such as entities) get added to the tlas TODO: implement entities

            //TODO: look into doing an update instead of a full tlas rebuild so instead just update the tlas to a new
            // acceleration structure!!!

            int[] perGeometryInstanceCount = buildDataManager.getInstancePerGeometryCount();

            var buildInfo = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack)
                    .sType$Default()
                    .mode(VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR)//TODO: explore using VK_BUILD_ACCELERATION_STRUCTURE_MODE_UPDATE_KHR to speedup build times
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR)
                    .ppGeometries(buildDataManager.getGeometryPointers())
                    .geometryCount(perGeometryInstanceCount.length);

            VkAccelerationStructureBuildSizesInfoKHR buildSizesInfo = VkAccelerationStructureBuildSizesInfoKHR
                    .calloc(stack)
                    .sType$Default();

            vkGetAccelerationStructureBuildSizesKHR(
                    context.device,
                    VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                    buildInfo.get(0),//The reason its a buffer is cause of pain and that vkCmdBuildAccelerationStructuresKHR requires a buffer of VkAccelerationStructureBuildGeometryInfoKHR
                    stack.ints(perGeometryInstanceCount),
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

            var cmd = context.cmd.singleTimeCommand();
            VFence fence = context.sync.createFence();
            //TODO: need to sync with respect to updates from gpu memory updates from TLASBuildDataManager
            // OR SOMETHING CAUSE WITH MULTIPLE FRAMES GOING AT ONCE the gpu state of TLASBuildDataManager needs to be synced with
            // the current build phase, and the gpu side needs to be updated accoringly and synced correctly

            vkCmdPipelineBarrier(cmd.buffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                    VkMemoryBarrier.calloc(1, stack)
                            .sType$Default()
                            .srcAccessMask(0)
                            .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT|VK_ACCESS_TRANSFER_READ_BIT),
                    null, null);

            buildDataManager.performMemoryUpdates(cmd, fence);

            vkCmdPipelineBarrier(cmd.buffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, 0,
                    VkMemoryBarrier.calloc(1, stack)
                            .sType$Default()
                            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_SHADER_READ_BIT),
                    null, null);

            var buildRanges = VkAccelerationStructureBuildRangeInfoKHR.calloc(perGeometryInstanceCount.length, stack);
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
            });

            return chain;
        }
    }

    //Manages entries in the VkAccelerationStructureInstanceKHR buffer, ment to reuse as much as possible and be very efficient
    private final class TLASBuildDataManager {
        //Needs a gpu buffer for the instance data, this can be reused
        //private VkAccelerationStructureInstanceKHR.Buffer buffer;

        private VkAccelerationStructureGeometryKHR.Buffer buffer;
        private BitSet available;


        public PointerBuffer getGeometryPointers() {
            /*
            for () {
                //The tlas divvied up into regions so that the geometry info can be split easily, thus when updating
                // dont have to update _all_ sections in the build structure

                //TODO: have this cached in an array, using the ppGeometries to list the geometries, should be faster
                geometryInfo.get()
                        .geometry(VkAccelerationStructureGeometryDataKHR.calloc(stack)
                                .instances(VkAccelerationStructureGeometryInstancesDataKHR.calloc(stack)
                                        .sType$Default()
                                        .data(null)))
                        .geometryType(VK_GEOMETRY_TYPE_INSTANCES_KHR)
                        .flags();//TODO: ADD VkGeometryFlagsKHR VK_GEOMETRY_OPAQUE_BIT_KHR
            }*/
            return PointerBuffer.allocateDirect(1);
        }

        public int[] getInstancePerGeometryCount() {
            return new int[]{0};
        }

        public void performMemoryUpdates(VCmdBuff cmd, VFence fence) {
            //TODO: just create an upload buffer

            context.sync.addCallback(fence, ()->{

            });
        }
    }
}
