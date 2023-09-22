package me.cortex.vulkanite.lib.memory;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_GL_ERROR_;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;

public class VGBuffer extends VBuffer {
    public final int glId;
    private final long vkMemory;
    VGBuffer(VmaAllocator.BufferAllocation allocation, int glId) {
        super(allocation);
        this.glId = glId;
        this.vkMemory = allocation.ai.deviceMemory();
    }

    @Override
    public void free() {
        glDeleteBuffers(glId);
        _CHECK_GL_ERROR_();
        MemoryManager.ExternalMemoryTracker.release(this.vkMemory);
        super.free();
    }
}
