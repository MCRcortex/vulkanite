package me.cortex.vulkanite.lib.shader;

import me.cortex.vulkanite.lib.base.VRef;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;

public record ShaderModule(
        VRef<VShader> shader, String name) {
    public void setupStruct(MemoryStack stack, VkPipelineShaderStageCreateInfo struct) {
        struct.sType$Default()
                .stage(shader.get().stage)
                .module(shader.get().module)
                .pName(stack.UTF8(name));
    }
}
