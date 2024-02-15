package me.cortex.vulkanite.lib.memory;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.base.VRef;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_GL_ERROR_;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;

public class VGBuffer extends VBuffer {
    public final int glId;
    private final long vkMemory;
    protected VGBuffer(VmaAllocator.BufferAllocation allocation, int usage, int glId) {
        super(allocation, usage);
        this.glId = glId;
        this.vkMemory = allocation.ai.deviceMemory();
    }

    public static VRef<VGBuffer> create(VmaAllocator.BufferAllocation allocation, int usage, int glId) {
        return new VRef<>(new VGBuffer(allocation, usage, glId));
    }

    @Override
    protected void free() {
        int glId = this.glId;
        Vulkanite.INSTANCE.addSyncedCallback(() -> {
            glDeleteBuffers(glId);
            _CHECK_GL_ERROR_();
            MemoryManager.ExternalMemoryTracker.release(this.vkMemory);
        });
        super.free();
    }
}
