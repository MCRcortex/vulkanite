package me.cortex.vulkanite.lib.descriptors;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VRef;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;

import java.nio.LongBuffer;
import java.util.Objects;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;

public final class VDescriptorSetLayout extends VObject implements Pointer {
    private final VContext ctx;
    public final long layout;
    public final int[] types;

    private VDescriptorSetLayout(VContext ctx, long layout, int[] types) {
        this.ctx = ctx;
        this.layout = layout;
        this.types = types;
    }

    public static VRef<VDescriptorSetLayout> create(VContext ctx, long layout, int[] types) {
        return new VRef<>(new VDescriptorSetLayout(ctx, layout, types));
    }

    @Override
    public long address() {
        return layout;
    }

    @Override
    protected void free() {
        Vulkanite.INSTANCE.removePoolByLayout(this);
        vkDestroyDescriptorSetLayout(ctx.device, layout, null);
    }
}
