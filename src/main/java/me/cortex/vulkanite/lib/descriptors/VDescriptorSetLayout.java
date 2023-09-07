package me.cortex.vulkanite.lib.descriptors;

import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import me.cortex.vulkanite.lib.base.VContext;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;

import java.nio.LongBuffer;
import java.util.Objects;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;

public final class VDescriptorSetLayout extends TrackedResourceObject implements Pointer {
    private final VContext ctx;
    public final long layout;
    public final int[] types;

    public VDescriptorSetLayout(VContext ctx, long layout, int[] types) {
        this.ctx = ctx;
        this.layout = layout;
        this.types = types;
    }

    @Override
    public long address() {
        return layout;
    }

    @Override
    public void free() {
        free0();
        vkDestroyDescriptorSetLayout(ctx.device, layout, null);
    }
}
