package me.cortex.vulkanite.compat;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.pipeline.RaytracePipelineBuilder;
import me.cortex.vulkanite.lib.shader.ShaderModule;
import me.cortex.vulkanite.lib.shader.VShader;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;

public class RaytracingShaderSet {
    public final int maxDepth = 1;

    private record RayHit(ShaderModule close, ShaderModule any, ShaderModule intersection) {}
    private final ShaderModule raygen;
    private final ShaderModule[] raymiss;
    private final RayHit[] rayhits;

    public RaytracingShaderSet(VContext ctx, RaytracingShaderSource source) {
        var shader = VShader.compileLoad(ctx, source.raygen, VK_SHADER_STAGE_RAYGEN_BIT_KHR);
        this.raygen = shader.get().named();

        this.raymiss = new ShaderModule[source.raymiss.length];
        for (int i = 0; i < raymiss.length; i++) {
            shader = VShader.compileLoad(ctx, source.raymiss[i], VK_SHADER_STAGE_MISS_BIT_KHR);
            this.raymiss[i] = shader.get().named();
        }

        this.rayhits = new RayHit[source.rayhit.length];
        for (int i = 0; i < this.rayhits.length; i++) {
            var hit = source.rayhit[i];

            ShaderModule close = null;
            if (hit.close() != null) {
                shader = VShader.compileLoad(ctx, hit.close(), VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR);
                close = shader.get().named();
            }

            ShaderModule any = null;
            if (hit.any() != null) {
                shader = VShader.compileLoad(ctx, hit.any(), VK_SHADER_STAGE_ANY_HIT_BIT_KHR);
                any = shader.get().named();
            }

            ShaderModule intersection = null;
            if (hit.intersection() != null) {
                shader = VShader.compileLoad(ctx, hit.intersection(), VK_SHADER_STAGE_INTERSECTION_BIT_KHR);
                intersection = shader.get().named();
            }

            this.rayhits[i] = new RayHit(close, any, intersection);
        }
    }

    public void apply(RaytracePipelineBuilder builder) {
        builder.setRayGen(raygen);
        for (var miss : raymiss) {
            builder.addMiss(miss);
        }
        for (var hit : rayhits) {
            builder.addHit(hit.close, hit.any, hit.intersection);
        }
    }

    public int getRayHitCount() {
        return rayhits.length;
    }
}
