package me.cortex.vulkanite.lib.cmd;

import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;

public class VCmdBuff implements Pointer {
    private final VCommandPool pool;
    public final VkCommandBuffer buffer;

    VCmdBuff(VCommandPool pool, VkCommandBuffer buff) {
        this.pool = pool;
        this.buffer = buff;
    }

    //Enqueues the pool to be freed by the owning thread
    public void enqueueFree() {
        pool.free(this);
    }

    public void begin(int flags) {
        try (var stack = stackPush()) {
            vkBeginCommandBuffer(buffer, VkCommandBufferBeginInfo.calloc(stack).sType$Default().flags(flags));
        }
    }

    public void end() {
        vkEndCommandBuffer(buffer);
    }

    @Override
    public long address() {
        return buffer.address();
    }
}
