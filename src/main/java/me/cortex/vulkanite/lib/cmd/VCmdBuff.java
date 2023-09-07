package me.cortex.vulkanite.lib.cmd;

import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;

//TODO: Track with TrackedResourceObject but need to be careful due to how the freeing works
public class VCmdBuff extends TrackedResourceObject implements Pointer {
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

    @Override
    public void free() {
        throw new IllegalStateException();
    }

    void freeInternal() {
        free0();
        vkFreeCommandBuffers(pool.device, pool.pool, buffer);
    }
}
