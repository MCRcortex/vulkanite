package me.cortex.vulkanite.lib.pipeline;

import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import me.cortex.vulkanite.lib.base.VContext;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;
import static org.lwjgl.vulkan.VK10.*;

public class VShader extends TrackedResourceObject {
    private final VContext ctx;
    public final long module;
    public final int stage;

    public VShader(VContext ctx, long module, int stage) {
        this.ctx = ctx;
        this.module = module;
        this.stage = stage;
    }

    public ShaderModule named() {
        return named("main");
    }

    public ShaderModule named(String name) {
        return new ShaderModule(this, name);
    }

    private static int vulkanStageToShadercKind(int stage) {
        switch (stage) {
            case VK_SHADER_STAGE_VERTEX_BIT:
                return shaderc_vertex_shader;
            case VK_SHADER_STAGE_FRAGMENT_BIT:
                return shaderc_fragment_shader;
            case VK_SHADER_STAGE_RAYGEN_BIT_KHR:
                return shaderc_raygen_shader;
            case VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR:
                return shaderc_closesthit_shader;
            case VK_SHADER_STAGE_MISS_BIT_KHR:
                return shaderc_miss_shader;
            case VK_SHADER_STAGE_ANY_HIT_BIT_KHR:
                return shaderc_anyhit_shader;
            case VK_SHADER_STAGE_INTERSECTION_BIT_KHR:
                return shaderc_intersection_shader;
            case VK_SHADER_STAGE_COMPUTE_BIT:
                return shaderc_compute_shader;
            default:
                throw new IllegalArgumentException("Stage: " + stage);
        }
    }

    public static ByteBuffer compileShader(String filename, String source, int vulkanStage) {
        long compiler = shaderc_compiler_initialize();
        if (compiler == 0) {
            throw new RuntimeException("Failed to create shader compiler");
        }
        long options = shaderc_compile_options_initialize();
        shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_2);
        shaderc_compile_options_set_target_spirv(options, shaderc_spirv_version_1_4);
        shaderc_compile_options_set_generate_debug_info(options);
        shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);

        long result = shaderc_compile_into_spv(compiler, source, vulkanStageToShadercKind(vulkanStage), filename, "main", options);

        if (result == 0) {
            throw new RuntimeException("Failed to compile shader " + filename + " into SPIR-V");
        }

        if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
            throw new RuntimeException("Failed to compile shader " + filename + "into SPIR-V:\n " + shaderc_result_get_error_message(result));
        }
        shaderc_compile_options_release(options);
        shaderc_compiler_release(compiler);
        ByteBuffer code = shaderc_result_get_bytes(result);
        var ret = ByteBuffer.allocateDirect(code.capacity()).put(code).rewind();
        shaderc_result_release(result);
        return ret;
    }

    public static VShader compileLoad(VContext ctx, String source, int stage) {
        try (var stack = stackPush()) {
            ByteBuffer code = compileShader("shader", source, stage);

            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(code);
            LongBuffer pShaderModule = stack.mallocLong(1);
            _CHECK_(vkCreateShaderModule(ctx.device, createInfo, null, pShaderModule));
            return new VShader(ctx, pShaderModule.get(0), stage);
        }
    }

    public void delete() {
        vkDestroyShaderModule(ctx.device, module, null);
    }

    @Override
    public void free() {

    }
}
