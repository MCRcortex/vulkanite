package me.cortex.vulkanite.lib.pipeline;

import me.cortex.vulkanite.lib.base.VContext;
import org.lwjgl.system.Pointer;

import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;

public record VDescriptorSetLayout(VContext ctx, long layout) implements Pointer {
    @Override
    public long address() {
        return layout;
    }

    public void destroy() {
        vkDestroyDescriptorSetLayout(ctx.device, layout, null);
    }
}
