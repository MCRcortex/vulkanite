package me.cortex.vulkanite.lib.memory;

import static me.cortex.vulkanite.lib.memory.MemoryManager.closeHandle;
import static me.cortex.vulkanite.lib.other.VUtil._CHECK_GL_ERROR_;
import static org.lwjgl.opengl.EXTMemoryObject.glDeleteMemoryObjectsEXT;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;

public class VGImage extends VImage {
    public final int glId;
    private final int glMemObj;
    public final int glFormat;
    private final long handle;

    VGImage(VmaAllocator.ImageAllocation allocation, int width, int height, int mipLayers, int format, int glFormat, int glId, int glMemObj, long handle) {
        super(allocation, width, height, mipLayers, format);
        this.glId = glId;
        this.glMemObj = glMemObj;
        this.glFormat = glFormat;
        this.handle = handle;
    }

    public void free() {
        closeHandle(handle);
      
        glDeleteTextures(glId);
        glDeleteMemoryObjectsEXT(glMemObj);
        _CHECK_GL_ERROR_();
        super.free();
    }
}
