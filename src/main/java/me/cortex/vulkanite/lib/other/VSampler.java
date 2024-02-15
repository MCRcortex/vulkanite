package me.cortex.vulkanite.lib.other;

import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.VRef;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;
import java.util.function.Consumer;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class VSampler extends VObject {
    private final VContext ctx;
    public final long sampler;

    private VSampler(VContext context, Consumer<VkSamplerCreateInfo> setup) {
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

    public static VRef<VSampler> create(VContext context, Consumer<VkSamplerCreateInfo> setup) {
        return new VRef<>(new VSampler(context, setup));
    }

    @Override
    protected void free() {
        vkDestroySampler(ctx.device, sampler, null);
    }
}
