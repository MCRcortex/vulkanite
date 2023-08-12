package me.cortex.vulkanite.lib.descriptors;

import me.cortex.vulkanite.lib.base.VContext;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;

import java.nio.LongBuffer;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;

public record VDescriptorSetLayout(VContext ctx, long layout, int[] types) implements Pointer {
    @Override
    public long address() {
        return layout;
    }

    public void destroy() {
        vkDestroyDescriptorSetLayout(ctx.device, layout, null);
    }
}
