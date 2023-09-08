package me.cortex.vulkanite.lib.memory;

import me.cortex.vulkanite.client.Vulkanite;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_GL_ERROR_;
import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers;
import static org.lwjgl.opengl.EXTMemoryObject.glDeleteMemoryObjectsEXT;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;

public class VGBuffer extends VBuffer {
    public final int glId;
    private final int glMemObj;
    VGBuffer(VmaAllocator.BufferAllocation allocation, int glId, int glMemObj) {
        super(allocation);
        this.glId = glId;
        this.glMemObj = glMemObj;
    }

    @Override
    public void free() {
        glFinish();
        Vulkanite.INSTANCE.getCtx().cmd.waitQueueIdle(0);
        glDeleteBuffers(glId);
        glDeleteMemoryObjectsEXT(glMemObj);
        _CHECK_GL_ERROR_();
        super.free();
    }
}
