package me.cortex.vulkanite.lib.memory;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_GL_ERROR_;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;

public class VGImage extends VImage {
    public final int glId;
    public final int glFormat;
    private final long vkMemory;

    VGImage(VmaAllocator.ImageAllocation allocation, int width, int height, int depth, int mipLayers, int format, int glFormat, int glId) {
        super(allocation, width, height, depth, mipLayers, format);
        this.glId = glId;
        this.glFormat = glFormat;
        this.vkMemory = allocation.ai.deviceMemory();
    }

    public void free() {      
        glDeleteTextures(glId);
        _CHECK_GL_ERROR_();
        MemoryManager.ExternalMemoryTracker.release(this.vkMemory);
        super.free();
    }
}
