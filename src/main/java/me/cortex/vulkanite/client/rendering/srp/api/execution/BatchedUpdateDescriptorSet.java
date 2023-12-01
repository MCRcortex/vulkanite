package me.cortex.vulkanite.client.rendering.srp.api.execution;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.srp.api.VirtualResourceMapper;
import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutCache;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalAccelerationResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalBufferResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalImageResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.Resource;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.descriptors.DescriptorUpdateBuilder;
import me.cortex.vulkanite.lib.descriptors.VTypedDescriptorPool;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.other.VImageView;
import me.cortex.vulkanite.lib.other.VSampler;

import java.util.List;

import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;

public class BatchedUpdateDescriptorSet {
    private static class Binder {

    }
    private static class BufferBinder extends Binder {

    }

    private static final VSampler sampler = new VSampler(Vulkanite.INSTANCE.getCtx(), a->a.magFilter(VK_FILTER_NEAREST)
            .minFilter(VK_FILTER_NEAREST)
            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
            .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
            .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
            .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
            .compareOp(VK_COMPARE_OP_NEVER)
            .maxLod(1)
            .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
            .maxAnisotropy(1.0f));


    private final Layout layout;
    private final VTypedDescriptorPool pool;
    private final List<List<Resource<?>>> bindings;
    public BatchedUpdateDescriptorSet(VContext ctx, VirtualResourceMapper mapper, Layout layout, List<List<Resource<?>>> bindings) {
        var cache = mapper.getLayoutCacheOrNull();
        if (cache == null) {
            throw new IllegalStateException("Null layout cache");
        }
        this.pool = new VTypedDescriptorPool(ctx, cache.getConcrete(layout), 0);
        this.bindings = bindings;
        this.layout = layout;
    }

    public long updateAndGetSet(VCmdBuff cmd) {
        var set = this.pool.allocateSet();
        var updater = new DescriptorUpdateBuilder(Vulkanite.INSTANCE.getCtx(), this.bindings.size())
                .set(set.set);
        for (int i = 0; i < this.bindings.size(); i++) {
            var binding = layout.getBindings().get(i);
            var value = this.bindings.get(i);
            if (binding.type == VK_DESCRIPTOR_TYPE_STORAGE_BUFFER) {
                updater.buffer(binding.index, 0, value.stream().map(a->((ExternalBufferResource)a).getConcrete()).toList());
            } else if (binding.type == VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER) {
                updater.uniform(binding.index, ((ExternalBufferResource)value.get(0)).getConcrete());
            } else if (binding.type == VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR) {
                updater.acceleration(binding.index, value.stream().map(a->((ExternalAccelerationResource)a).getConcrete()).toArray(VAccelerationStructure[]::new));
            } else if (binding.type == VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER) {
                //TODO:FIXME:DOnt constantly create a new image just to free it
                var image = ((ExternalImageResource)value.get(0)).getConcrete();
                if (image != null) {
                    var view = new VImageView(Vulkanite.INSTANCE.getCtx(), image);
                    updater.imageSampler(binding.index, view, sampler);
                    cmd.addTransientResource(view);
                }
            } else if (binding.type == VK_DESCRIPTOR_TYPE_STORAGE_IMAGE) {
                //TODO:FIXME:DOnt constantly create a new image just to free it
                var image = ((ExternalImageResource)value.get(0)).getConcrete();
                if (image != null) {
                    var view = new VImageView(Vulkanite.INSTANCE.getCtx(), image);
                    updater.imageStore(binding.index, view);
                    cmd.addTransientResource(view);
                }
            } else {
                throw new IllegalStateException("Unknown binding type: " + binding.type);
            }
        }
        updater.apply();
        cmd.addTransientResource(set);
        return set.set;
    }

    public void destroy() {
        this.pool.free();
    }
}
