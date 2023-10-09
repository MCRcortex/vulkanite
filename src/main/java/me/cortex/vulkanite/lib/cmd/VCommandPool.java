package me.cortex.vulkanite.lib.cmd;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;
import java.util.concurrent.ConcurrentLinkedDeque;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class VCommandPool extends TrackedResourceObject {
    final VkDevice device;
    public final long pool;
    public VCommandPool(VkDevice device, int flags) {
        this(device, 0, flags);
    }

    public VCommandPool(VkDevice device, int family, int flags) {
        this.device = device;
        try (var stack = stackPush()) {
            VkCommandPoolCreateInfo cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(family)
                    .flags(flags);
            LongBuffer pCmdPool = stack.mallocLong(1);
            _CHECK_(vkCreateCommandPool(device, cmdPoolInfo, null, pCmdPool));
            pool = pCmdPool.get(0);
        }
    }

    public synchronized VCmdBuff createCommandBuffer() {
        return createCommandBuffers(1)[0];
    }

    public synchronized VCmdBuff[] createCommandBuffers(int count) {
        return createCommandBuffers(count, 0);
    }

    public synchronized VCmdBuff[] createCommandBuffers(int count, int level) {
        try (MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer pCommandBuffer = stack.mallocPointer(count);
            _CHECK_(vkAllocateCommandBuffers(device,
                            VkCommandBufferAllocateInfo
                                    .calloc(stack)
                                    .sType$Default()
                                    .commandPool(pool)
                                    .level(level)
                                    .commandBufferCount(count), pCommandBuffer),
                    "Failed to create command buffer");
            VCmdBuff[] buffers = new VCmdBuff[count];
            for (int i = 0; i < count; i++) {
                buffers[i] = new VCmdBuff(this, new VkCommandBuffer(pCommandBuffer.get(i), device));
            }
            return buffers;
        }
    }

    private final ConcurrentLinkedDeque<VCmdBuff> toRelease = new ConcurrentLinkedDeque<>();
    void free(VCmdBuff cmdBuff) {
        toRelease.add(cmdBuff);
    }

    public void doReleases() {
        while (!toRelease.isEmpty()) {
            toRelease.poll().freeInternal();
        }
    }

    public void releaseNow(VCmdBuff cmd) {
        cmd.freeInternal();
    }

    @Override
    public void free() {
        free0();
        vkDestroyCommandPool(device, pool, null);
    }

    public void setDebugUtilsObjectName(String name) {
        Vulkanite.INSTANCE.getCtx().setDebugUtilsObjectName(pool, VK_OBJECT_TYPE_COMMAND_POOL, name);
    }
}
