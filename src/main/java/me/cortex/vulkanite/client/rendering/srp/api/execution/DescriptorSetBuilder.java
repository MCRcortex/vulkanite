package me.cortex.vulkanite.client.rendering.srp.api.execution;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.SharedImageViewTracker;
import me.cortex.vulkanite.client.rendering.srp.api.VirtualResourceMapper;
import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutCache;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.*;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.descriptors.*;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.memory.VImage;
import me.cortex.vulkanite.lib.other.VImageView;
import me.cortex.vulkanite.lib.other.VSampler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT;

//TODO: make into a cache thing so that it can reuse descriptor sets and VTypedDescriptorPool
//TODO: FIX THIS ENTIRE SHITTY CLASS
public class DescriptorSetBuilder {
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

    public static class BatchedUpdateDescriptorSet {
        private final Layout layout;
        private final VTypedDescriptorPool pool;
        private final List<List<Resource<?>>> bindings;
        public BatchedUpdateDescriptorSet(Layout layout, VTypedDescriptorPool pool, List<List<Resource<?>>> bindings) {
            this.pool = pool;
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
    }

    public static BatchedUpdateDescriptorSet createDescriptorSet(VirtualResourceMapper mapper, Layout layout, List<List<Resource<?>>> bindings) {
        VDescriptorSetLayout layoutObj = new LayoutCache().getConcrete(layout);//TODO: FIXME
        var pool = new VTypedDescriptorPool(Vulkanite.INSTANCE.getCtx(), layoutObj, 0);
        return new BatchedUpdateDescriptorSet(layout, pool, bindings);
    }
}
