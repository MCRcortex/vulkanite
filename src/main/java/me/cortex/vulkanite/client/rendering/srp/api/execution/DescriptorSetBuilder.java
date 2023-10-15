package me.cortex.vulkanite.client.rendering.srp.api.execution;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.srp.api.VirtualResourceMapper;
import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.Resource;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.descriptors.DescriptorSetLayoutBuilder;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSet;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSetLayout;
import me.cortex.vulkanite.lib.descriptors.VTypedDescriptorPool;

import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_ALL;
import static org.lwjgl.vulkan.VK12.VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT;

//TODO: make into a cache thing so that it can reuse descriptor sets and VTypedDescriptorPool
public class DescriptorSetBuilder {
    public static class BatchedUpdateDescriptorSet {
        private final VTypedDescriptorPool pool;
        public BatchedUpdateDescriptorSet(VTypedDescriptorPool pool) {
            this.pool = pool;
        }

        public long updateAndGetSet(VCmdBuff cmd) {
            var set = pool.allocateSet();
            cmd.addTransientResource(set);
            return set.set;
        }
    }

    public static BatchedUpdateDescriptorSet createDescriptorSet(VirtualResourceMapper mapper, Layout layout, List<List<Resource<?>>> bindings) {
        var layoutObj = createLayoutObject(layout);
        var pool = new VTypedDescriptorPool(Vulkanite.INSTANCE.getCtx(), layoutObj, 0);
        return new BatchedUpdateDescriptorSet(pool);
    }

    private static VDescriptorSetLayout createLayoutObject(Layout layout) {
        if (layout.hasUnsizedArrays()) {
            throw new IllegalStateException("User defined layouts that are runtime sized are not supported");
        }

        var builder = new DescriptorSetLayoutBuilder();
        for (var binding : layout.getBindings()) {
            if (binding.arraySize > 0) {
                builder.binding(binding.index, binding.type, binding.arraySize, VK_SHADER_STAGE_ALL);
                builder.setBindingFlags(binding.index, VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT);
            } else {
                builder.binding(binding.index, binding.type, VK_SHADER_STAGE_ALL);
            }
        }

        return builder.build(Vulkanite.INSTANCE.getCtx());
    }
}
