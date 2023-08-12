package me.cortex.vulkanite.client.rendering;

import me.cortex.vulkanite.acceleration.AccelerationManager;
import me.cortex.vulkanite.lib.base.VContext;

import static org.lwjgl.opengl.EXTSemaphore.glSignalSemaphoreEXT;
import static org.lwjgl.opengl.GL11C.glFlush;

public class VulkanPipeline {
    private final VContext ctx;
    private final AccelerationManager accelerationManager;

    public VulkanPipeline(VContext ctx, AccelerationManager accelerationManager) {
        this.ctx = ctx;
        this.accelerationManager = accelerationManager;
    }

    public void renderPostShadows() {
        var in = ctx.sync.createSharedBinarySemaphore();
        var out = ctx.sync.createSharedBinarySemaphore();

        var tlas = accelerationManager.buildTLAS(in, out);
        if (tlas != null) {
            in.glSignal(new int[0], new int[0], new int[0]);
            glFlush();
            out.glWait(new int[0], new int[0], new int[0]);
            glFlush();
        }
    }
}
