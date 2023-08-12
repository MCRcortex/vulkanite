package me.cortex.vulkanite.lib.other.sync;

import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.opengl.EXTSemaphore.*;

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

    //Note: dstLayout is for the textures
    public void glSignal(int[] buffers, int[] textures, int[] dstLayouts) {
        glSignalSemaphoreEXT(glSemaphore, buffers, textures, dstLayouts);
    }

    //Note: srcLayout is for the textures
    public void glWait(int[] buffers, int[] textures, int[] srcLayouts) {
        glWaitSemaphoreEXT(glSemaphore, buffers, textures, srcLayouts);
    }
}
