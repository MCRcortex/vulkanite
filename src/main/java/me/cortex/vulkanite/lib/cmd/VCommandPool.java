package me.cortex.vulkanite.lib.cmd;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.base.VObject;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class VCommandPool extends VObject {
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

    public synchronized VRef<VCmdBuff> createCommandBuffer() {
        return createCommandBuffers(1).get(0);
    }

    public synchronized List<VRef<VCmdBuff>> createCommandBuffers(int count) {
        return createCommandBuffers(count, 0, VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
    }

    public synchronized List<VRef<VCmdBuff>> createCommandBuffers(int count, int level, int flags) {
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
            List<VRef<VCmdBuff>> buffers = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                buffers.add(new VRef<>(new VCmdBuff(this, new VkCommandBuffer(pCommandBuffer.get(i), device), flags)));
            }
            return buffers;
        }
    }

    @Override
    public void free() {
        vkDestroyCommandPool(device, pool, null);
    }

    public void setDebugUtilsObjectName(String name) {
        Vulkanite.INSTANCE.getCtx().setDebugUtilsObjectName(pool, VK_OBJECT_TYPE_COMMAND_POOL, name);
    }
}
