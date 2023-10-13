package me.cortex.vulkanite.client.rendering;

import me.cortex.vulkanite.acceleration.AccelerationManager;
import me.cortex.vulkanite.compat.RaytracingShaderSet;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCommandPool;
import me.cortex.vulkanite.lib.memory.VGImage;

public class VkPipeline2 {
    private final VContext ctx;
    private final AccelerationManager accelerationManager;
    private final VCommandPool singleUsePool;


    public VkPipeline2(VContext ctx, AccelerationManager accelerationManager, RaytracingShaderSet[] passes, int[] ssboIds, VGImage[] customTextures) {
        this.ctx = ctx;
        this.accelerationManager = accelerationManager;
        this.singleUsePool = ctx.cmd.createSingleUsePool();

    }

    private void buildGraph() {

    }
}
