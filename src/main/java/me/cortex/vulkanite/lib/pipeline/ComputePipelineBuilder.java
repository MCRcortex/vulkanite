package me.cortex.vulkanite.lib.pipeline;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSetLayout;
import me.cortex.vulkanite.lib.shader.ShaderModule;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.*;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static me.cortex.vulkanite.lib.other.VUtil.alignUp;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.*;

public class ComputePipelineBuilder {
    public ComputePipelineBuilder() {

    }

    Set<VDescriptorSetLayout> layouts = new LinkedHashSet<>();
    public ComputePipelineBuilder addLayout(VDescriptorSetLayout layout) {
        layouts.add(layout);
        return this;
    }

    private ShaderModule compute;
    public ComputePipelineBuilder set(ShaderModule shader) {
        this.compute = shader;
        return this;
    }

    public VComputePipeline build(VContext context) {
        try (var stack = stackPush()) {

            VkPipelineLayoutCreateInfo layoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default();
            {
                //TODO: cleanup and add push constants
                layoutCreateInfo.pSetLayouts(stack.longs(layouts.stream().mapToLong(a->a.layout).toArray()));
            }

            LongBuffer pLayout = stack.mallocLong(1);
            _CHECK_(vkCreatePipelineLayout(context.device, layoutCreateInfo, null, pLayout));

            VkPipelineShaderStageCreateInfo shaderStage = VkPipelineShaderStageCreateInfo.calloc(stack);
            compute.setupStruct(stack, shaderStage);
            LongBuffer pPipeline = stack.mallocLong(1);
            _CHECK_(vkCreateComputePipelines(context.device, 0, VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .layout(pLayout.get(0))
                    .stage(shaderStage), null, pPipeline));

            return new VComputePipeline(context, pLayout.get(0), pPipeline.get(0));
        }
    }
}
