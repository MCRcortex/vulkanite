package me.cortex.vulkanite.lib.other.sync;

import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.opengl.EXTSemaphore.glDeleteSemaphoresEXT;

public class VGSemaphore extends VSemaphore {
    public final int glSemaphore;

    public VGSemaphore(VkDevice device, long semaphore, int glSemaphore) {
        super(device, semaphore);
        this.glSemaphore = glSemaphore;
    }

    @Override
    public void free() {
        glDeleteSemaphoresEXT(glSemaphore);
        super.free();
    }
}
