package me.cortex.vulkanite.lib.other;

import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import me.cortex.vulkanite.lib.base.VContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;
import java.util.function.Consumer;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class VSampler extends TrackedResourceObject {
    private final VContext ctx;
    public final long sampler;

    public VSampler(VContext context, Consumer<VkSamplerCreateInfo> setup) {
        this.ctx = context;
        try (MemoryStack stack = stackPush()) {
            LongBuffer pSampler = stack.mallocLong(1);
            var create = VkSamplerCreateInfo
                    .calloc(stack)
                    .sType$Default();
            setup.accept(create);
            _CHECK_(vkCreateSampler(ctx.device, create, null, pSampler),
                    "Failed to create sampler");
            sampler = pSampler.get(0);
        }
    }

    @Override
    public void free() {
        free0();
        vkDestroySampler(ctx.device, sampler, null);
    }
}
