package me.cortex.vulkanite.lib.cmd;

import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkFreeCommandBuffers;

public class VCmdBuff implements Pointer {
    private final VCommandPool pool;
    public final VkCommandBuffer buffer;
    public VCmdBuff(VCommandPool pool, VkCommandBuffer buff) {
        this.pool = pool;
        this.buffer = buff;
    }



    //Note: contains no syncing, immediately frees
    public void freeNow() {
        try (var stack = stackPush()) {
            vkFreeCommandBuffers(pool.device, pool.pool, buffer);
        }
    }

    @Override
    public long address() {
        return buffer.address();
    }
}
