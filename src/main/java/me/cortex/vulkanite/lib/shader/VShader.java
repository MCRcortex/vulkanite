package me.cortex.vulkanite.lib.shader;

import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.shader.reflection.ShaderReflection;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;
import static org.lwjgl.vulkan.VK10.*;

public class VShader extends VObject {
    private final VContext ctx;
    public final long module;
    public final int stage;
    public final ShaderReflection reflection;

    public ShaderReflection getReflection() {
        return reflection;
    }

    private VShader(VContext ctx, long module, int stage, ShaderReflection reflection) {
        this.ctx = ctx;
        this.module = module;
        this.stage = stage;
        this.reflection = reflection;
    }

    public ShaderModule named() {
        return named("main");
    }

    public ShaderModule named(String name) {
        return new ShaderModule(new VRef<>(this), name);
    }

    public static VRef<VShader> compileLoad(VContext ctx, String source, int stage) {
        try (var stack = stackPush()) {
            ByteBuffer code = ShaderCompiler.compileShader("shader", source, stage);

            ShaderReflection reflection = new ShaderReflection(code);

            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(code);
            LongBuffer pShaderModule = stack.mallocLong(1);
            _CHECK_(vkCreateShaderModule(ctx.device, createInfo, null, pShaderModule));
            return new VRef<>(new VShader(ctx, pShaderModule.get(0), stage, reflection));
        }
    }

    @Override
    protected void free() {
        vkDestroyShaderModule(ctx.device, module, null);
    }
}
