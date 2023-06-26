package me.cortex.vulkanite.acceleration;

//TLAS manager, ingests blas build requests and manages builds and syncs the tlas

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import org.lwjgl.vulkan.VkAccelerationStructureInstanceKHR;

import java.util.List;

public class AccelerationTLASManager {
    private final TLASBuildDataManager buildDataManager = new TLASBuildDataManager();

    public AccelerationTLASManager(VContext context) {

    }

    //Returns a sync semaphore to chain in the next command submit
    public VSemaphore updateTLAS(List<VSemaphore> syncs, List<AccelerationBlasBuilder.BLASBuildResult> results) {
        return null;
    }

    //Manages entries in the VkAccelerationStructureInstanceKHR buffer, ment to reuse as much as possible and be very efficient
    private static final class TLASBuildDataManager {
        private VkAccelerationStructureInstanceKHR.Buffer buffer;

    }
}
