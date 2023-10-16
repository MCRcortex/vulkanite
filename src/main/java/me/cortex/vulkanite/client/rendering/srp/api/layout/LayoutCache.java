package me.cortex.vulkanite.client.rendering.srp.api.layout;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.descriptors.DescriptorSetLayoutBuilder;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSetLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_ALL;
import static org.lwjgl.vulkan.VK12.*;

//Cache for Layouts -> VDescriptorSetLayout
public class LayoutCache {
    private static final int MAX_RUNTIME_LAYOUT_SIZE = (1<<16);

    private final Map<Layout, VDescriptorSetLayout> cache = new HashMap<>();

    public VDescriptorSetLayout getConcrete(Layout layout) {
        return this.cache.computeIfAbsent(layout, this::createPhysicalLayout);
    }

    private VDescriptorSetLayout createPhysicalLayout(Layout layout) {
        var builder = new DescriptorSetLayoutBuilder();
        for (var binding : layout.getBindings()) {
            if (binding.arraySize == -1) {
                builder.binding(binding.index, binding.type, MAX_RUNTIME_LAYOUT_SIZE, VK_SHADER_STAGE_ALL);
                builder.setBindingFlags(binding.index,
                        VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT
                                | VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT
                                | VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT);
            } else if (binding.arraySize > 0) {
                builder.binding(binding.index, binding.type, binding.arraySize, VK_SHADER_STAGE_ALL);
                builder.setBindingFlags(binding.index, VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT);
            } else {
                builder.binding(binding.index, binding.type, VK_SHADER_STAGE_ALL);
            }
        }

        return builder.build(Vulkanite.INSTANCE.getCtx());
    }

    public void freeAll() {
        this.cache.forEach((layout, physical)->physical.free());
        this.cache.clear();
    }
}
