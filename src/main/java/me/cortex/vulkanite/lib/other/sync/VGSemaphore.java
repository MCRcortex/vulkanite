package me.cortex.vulkanite.lib.other.sync;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.memory.HandleDescriptorManger;
import me.cortex.vulkanite.lib.memory.MemoryManager;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.opengl.EXTSemaphore.*;

public class VGSemaphore extends VSemaphore {
    public final int glSemaphore;
    private final long handleDescriptor;

    protected VGSemaphore(VkDevice device, long semaphore, int glSemaphore, long handleDescriptor) {
        super(device, semaphore);
        this.glSemaphore = glSemaphore;
        this.handleDescriptor = handleDescriptor;
    }

    public static VRef<VGSemaphore> create(VkDevice device, long semaphore, int glSemaphore, long handleDescriptor) {
        return new VRef<>(new VGSemaphore(device, semaphore, glSemaphore, handleDescriptor));
    }

    @Override
    protected void free() {
        HandleDescriptorManger.close(handleDescriptor);
        int glSemaphore = this.glSemaphore;
        Vulkanite.INSTANCE.addSyncedCallback(() -> {
            glDeleteSemaphoresEXT(glSemaphore);
        });
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
