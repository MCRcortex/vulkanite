package me.cortex.vulkanite.lib.pipeline;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;

public record ShaderModule(VShader shader, String name) {
    void setupStruct(MemoryStack stack, VkPipelineShaderStageCreateInfo struct) {
        struct.sType$Default()
                .stage(shader.stage)
                .module(shader.module)
                .pName(stack.UTF8(name));
    }
}
