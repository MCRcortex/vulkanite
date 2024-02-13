package me.cortex.vulkanite.lib.pipeline;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.VRef;
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

    Set<VRef<VDescriptorSetLayout>> layouts = new LinkedHashSet<>();
    public ComputePipelineBuilder addLayout(VRef<VDescriptorSetLayout> layout) {
        layouts.add(layout);
        return this;
    }

    private ShaderModule compute;
    public ComputePipelineBuilder set(ShaderModule shader) {
        this.compute = shader;
        return this;
    }

    private record PushConstant(int size, int offset) {}
    private List<PushConstant> pushConstants = new ArrayList<>();

    public void addPushConstantRange(int size, int offset) {
        pushConstants.add(new PushConstant(size, offset));
    }

    public VRef<VComputePipeline> build(VContext context) {
        try (var stack = stackPush()) {

            VkPipelineLayoutCreateInfo layoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default();
            {
                //TODO: cleanup and add push constants
                layoutCreateInfo.pSetLayouts(stack.longs(layouts.stream().mapToLong(a->a.get().layout).toArray()));
            }

            if (pushConstants.size() > 0) {
                var pushConstantRanges = VkPushConstantRange.calloc(pushConstants.size(), stack);
                for (int i = 0; i < pushConstants.size(); i++) {
                    var pushConstant = pushConstants.get(i);
                    pushConstantRanges.get(i)
                            .stageFlags(VK_SHADER_STAGE_ALL)
                            .offset(pushConstant.offset)
                            .size(pushConstant.size);
                }
                layoutCreateInfo.pPushConstantRanges(pushConstantRanges);
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

            return new VRef<>(new VComputePipeline(context, pLayout.get(0), pPipeline.get(0)));
        }
    }
}
