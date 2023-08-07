package me.cortex.vulkanite.lib.pipeline;

import me.cortex.vulkanite.lib.base.VContext;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkRayTracingPipelineCreateInfoKHR;
import org.lwjgl.vulkan.VkRayTracingShaderGroupCreateInfoKHR;

import java.nio.LongBuffer;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_SHADER_STAGE_RAYGEN_BIT_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.vkCreateRayTracingPipelinesKHR;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;

public class RaytracePipelineBuilder {
    public RaytracePipelineBuilder() {

    }


    //TODO: generate stb
    public VRaytracePipeline build(VContext context) {

        try (var stack = stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(1, stack);

            VkRayTracingShaderGroupCreateInfoKHR.Buffer groupsArr = VkRayTracingShaderGroupCreateInfoKHR
                    .calloc(1, stack);

            VkPipelineLayoutCreateInfo layoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default();

            {
                shaderStages.get(0)
                        .sType$Default()
                        .stage(VK_SHADER_STAGE_RAYGEN_BIT_KHR)
                        .pName(stack.UTF8("yort"));

                groupsArr.get(0)
                        .sType$Default()
                        .intersectionShader(0);
            }


            LongBuffer pLayout = stack.mallocLong(1);
            _CHECK_(vkCreatePipelineLayout(context.device, layoutCreateInfo, null, pLayout));


            var pipelineCreateInfo = VkRayTracingPipelineCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .layout(pLayout.get(0))
                    .pStages(shaderStages)
                    .pGroups(groupsArr)
                    .maxPipelineRayRecursionDepth(1);

            LongBuffer pPipeline = stack.mallocLong(1);
            _CHECK_(vkCreateRayTracingPipelinesKHR(context.device, VK_NULL_HANDLE, VK_NULL_HANDLE,
                    VkRayTracingPipelineCreateInfoKHR.create(pipelineCreateInfo.address(), 1),
                    null, pPipeline));

            return new VRaytracePipeline();
        }
    }
}
