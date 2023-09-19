package me.cortex.vulkanite.mixin.iris;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.VulkanPipeline;
import me.cortex.vulkanite.compat.IGetRaytracingSource;
import me.cortex.vulkanite.compat.IRenderTargetVkGetter;
import me.cortex.vulkanite.compat.RaytracingShaderSet;
import me.cortex.vulkanite.lib.base.VContext;
import net.coderbot.iris.gl.buffer.ShaderStorageBuffer;
import net.coderbot.iris.gl.buffer.ShaderStorageBufferHolder;
import net.coderbot.iris.gl.buffer.ShaderStorageInfo;
import net.coderbot.iris.mixin.LevelRendererAccessor;
import net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.shaderpack.ProgramSet;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NewWorldRenderingPipeline.class, remap = false)
public class MixinNewWorldRenderingPipeline {
    @Shadow @Final private RenderTargets renderTargets;
    @Shadow private ShaderStorageBufferHolder shaderStorageBufferHolder;
    @Unique private RaytracingShaderSet[] rtShaderPasses = null;
    @Unique private VContext ctx;
    @Unique private VulkanPipeline pipeline;


    @Inject(method = "<init>", at = @At("TAIL"))
    private void injectRTShader(ProgramSet set, CallbackInfo ci) {
        ctx = Vulkanite.INSTANCE.getCtx();
        var passes = ((IGetRaytracingSource)set).getRaytracingSource();
        if (passes != null) {
            rtShaderPasses = new RaytracingShaderSet[passes.length];
            for (int i = 0; i < passes.length; i++) {
                rtShaderPasses[i] = new RaytracingShaderSet(ctx, passes[i]);
            }

            pipeline = new VulkanPipeline(ctx, Vulkanite.INSTANCE.getAccelerationManager(), rtShaderPasses, set.getPackDirectives().getBufferObjects().keySet().toArray(new int[0]));
        }
    }

    @Inject(method = "renderShadows", at = @At("TAIL"))
    private void renderShadows(LevelRendererAccessor par1, Camera par2, CallbackInfo ci) {
        pipeline.renderPostShadows(((IRenderTargetVkGetter)renderTargets.getOrCreate(0)).getMain(), par2, ((ShaderStorageBufferHolderAccessor)shaderStorageBufferHolder).getBuffers());
    }

    @Inject(method = "destroyShaders", at = @At("TAIL"))
    private void destory(CallbackInfo ci) {
        if (rtShaderPasses != null) {
            ctx.cmd.waitQueueIdle(0);
            for (var pass : rtShaderPasses) {
                pass.delete();
            }
            pipeline.destory();
            rtShaderPasses = null;
            pipeline = null;
        }
    }
}
