package me.cortex.vulkanite.lib.memory;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_GL_ERROR_;
import static org.lwjgl.opengl.EXTMemoryObject.glDeleteMemoryObjectsEXT;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;

public class VGImage extends VImage {
    public final int glId;
    private final int glMemObj;
    public final int glFormat;
    VGImage(VmaAllocator.ImageAllocation allocation, int width, int height, int mipLayers, int format, int glFormat, int glId, int glMemObj) {
        super(allocation, width, height, mipLayers, format);
        this.glId = glId;
        this.glMemObj = glMemObj;
        this.glFormat = glFormat;
    }

    public void free() {
        glDeleteTextures(glId);
        glDeleteMemoryObjectsEXT(glMemObj);
        _CHECK_GL_ERROR_();
        super.free();
    }
}
