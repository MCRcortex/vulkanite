package me.cortex.vulkanite.mixin.iris;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry;
import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.VulkanPipeline;
import me.cortex.vulkanite.compat.IGetRaytracingSource;
import me.cortex.vulkanite.compat.IRenderTargetVkGetter;
import me.cortex.vulkanite.compat.IVGImage;
import me.cortex.vulkanite.compat.RaytracingShaderSet;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.memory.VGImage;
import net.coderbot.iris.gl.buffer.ShaderStorageBuffer;
import net.coderbot.iris.gl.texture.TextureAccess;
import net.coderbot.iris.gl.buffer.ShaderStorageBufferHolder;
import net.coderbot.iris.mixin.LevelRendererAccessor;
import net.coderbot.iris.pipeline.CustomTextureManager;
import net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.shaderpack.ProgramSet;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import net.minecraft.client.render.Camera;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(value = NewWorldRenderingPipeline.class, remap = false)
public class MixinNewWorldRenderingPipeline {
  
    @Shadow @Final private RenderTargets renderTargets;
    @Shadow @Final private CustomTextureManager customTextureManager;
    @Shadow private ShaderStorageBufferHolder shaderStorageBufferHolder;
  
    @Unique private RaytracingShaderSet[] rtShaderPasses = null;
    @Unique private VContext ctx;
    @Unique private VulkanPipeline pipeline;

    @Unique
    private VGImage[] getCustomTextures() {
        Object2ObjectMap<String, TextureAccess> texturesBinary = customTextureManager.getIrisCustomTextures();
        Object2ObjectMap<String, TextureAccess> texturesPNGs = customTextureManager.getCustomTextureIdMap(TextureStage.GBUFFERS_AND_SHADOW);

        List<Entry<String, TextureAccess>> entryList = new ArrayList<>();
        entryList.addAll(texturesBinary.object2ObjectEntrySet());
        entryList.addAll(texturesPNGs.object2ObjectEntrySet());

        entryList.sort(Comparator.comparing(Entry::getKey));

        VGImage[] sortedTextures = entryList.stream()
                .map(entry -> ((IVGImage) entry.getValue()).getVGImage())
                .toArray(VGImage[]::new);

        return sortedTextures;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void injectRTShader(ProgramSet set, CallbackInfo ci) {
        ctx = Vulkanite.INSTANCE.getCtx();
        var passes = ((IGetRaytracingSource)set).getRaytracingSource();
        if (passes != null) {
            rtShaderPasses = new RaytracingShaderSet[passes.length];
            for (int i = 0; i < passes.length; i++) {
                rtShaderPasses[i] = new RaytracingShaderSet(ctx, passes[i]);
            }

            pipeline = new VulkanPipeline(ctx, Vulkanite.INSTANCE.getAccelerationManager(), rtShaderPasses, set.getPackDirectives().getBufferObjects().keySet().toArray(new int[0]), getCustomTextures());
        }
    }

    @Inject(method = "renderShadows", at = @At("TAIL"))
    private void renderShadows(LevelRendererAccessor par1, Camera par2, CallbackInfo ci) {
        ShaderStorageBuffer[] buffers = new ShaderStorageBuffer[0];

        if(shaderStorageBufferHolder != null) {
            buffers = ((ShaderStorageBufferHolderAccessor)shaderStorageBufferHolder).getBuffers();
        }

        pipeline.renderPostShadows(((IRenderTargetVkGetter)renderTargets.getOrCreate(0)).getMain(), par2, buffers);
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
