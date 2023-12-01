package me.cortex.vulkanite.mixin.iris;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry;
import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.VkPipeline2;
import me.cortex.vulkanite.client.rendering.srp.lua.LuaContextHost;
import me.cortex.vulkanite.compat.*;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.memory.VGImage;
import net.coderbot.iris.gl.buffer.ShaderStorageBuffer;
import net.coderbot.iris.gl.texture.TextureAccess;
import net.coderbot.iris.gl.buffer.ShaderStorageBufferHolder;
import net.coderbot.iris.gl.uniform.DynamicUniformHolder;
import net.coderbot.iris.mixin.LevelRendererAccessor;
import net.coderbot.iris.pipeline.CustomTextureManager;
import net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.shaderpack.ProgramSet;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import net.coderbot.iris.uniforms.CelestialUniforms;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.render.Camera;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(value = NewWorldRenderingPipeline.class, remap = false)
public class MixinNewWorldRenderingPipeline {
  
    @Shadow @Final private RenderTargets renderTargets;
    @Shadow @Final private CustomTextureManager customTextureManager;
    @Shadow private ShaderStorageBufferHolder shaderStorageBufferHolder;

    @Shadow @Final private float sunPathRotation;
    @Unique private VContext ctx;
    @Unique private VkPipeline2 pipeline;

    @Unique
    private VGImage[] getCustomTextures() {
        Object2ObjectMap<String, TextureAccess> texturesBinary = customTextureManager.getIrisCustomTextures();
        Object2ObjectMap<String, TextureAccess> texturesPNGs = customTextureManager.getCustomTextureIdMap(TextureStage.GBUFFERS_AND_SHADOW);

        List<Entry<String, TextureAccess>> entryList = new ArrayList<>();
        entryList.addAll(texturesBinary.object2ObjectEntrySet());
        entryList.addAll(texturesPNGs.object2ObjectEntrySet());

        entryList.sort(Comparator.comparing(Entry::getKey));

        return entryList.stream()
                .map(entry -> ((IVGImage) entry.getValue()).getVGImage())
                .toArray(VGImage[]::new);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void injectRTShader(ProgramSet set, CallbackInfo ci) {
        ctx = Vulkanite.INSTANCE.getCtx();
        if (this.pipeline != null) {
            this.pipeline.destory();
        }

        pipeline = new VkPipeline2(ctx, new LuaContextHost(path -> {
            try {
                return Files.readAllBytes(new File("shaderpacks/testpack/shaders/" + path).toPath());
            } catch (
                    IOException e) {
                throw new RuntimeException(e);
            }
        }), Vulkanite.INSTANCE.getAccelerationManager());

        /*
        var passes = ((IGetRaytracingSource)set).getRaytracingSource();
        if (passes != null) {
            rtShaderPasses = new RaytracingShaderSet[passes.length];
            for (int i = 0; i < passes.length; i++) {
                rtShaderPasses[i] = new RaytracingShaderSet(ctx, passes[i]);
            }
        }
        // Still create this, later down the line we might add Vulkan compute pipelines or mesh shading, etc.
        pipeline = new VulkanPipeline(ctx, Vulkanite.INSTANCE.getAccelerationManager(), rtShaderPasses, set.getPackDirectives().getBufferObjects().keySet().toArray(new int[0]), getCustomTextures());
        */
    }

    @Inject(method = "renderShadows", at = @At("TAIL"))
    private void renderShadows(LevelRendererAccessor par1, Camera par2, CallbackInfo ci) {
        /*
        ShaderStorageBuffer[] buffers = new ShaderStorageBuffer[0];

        if(shaderStorageBufferHolder != null) {
            buffers = ((ShaderStorageBufferHolderAccessor)shaderStorageBufferHolder).getBuffers();
        }

        List<VGImage> outImgs = new ArrayList<>();
        for (int i = 0; i < renderTargets.getRenderTargetCount(); i++) {
            outImgs.add(((IRenderTargetVkGetter)renderTargets.getOrCreate(i)).getMain());
        }
        */

        //MixinCelestialUniforms celestialUniforms = (MixinCelestialUniforms)(Object) new CelestialUniforms(this.sunPathRotation);

        //pipeline.renderPostShadows(outImgs, par2, buffers, celestialUniforms);
        pipeline.setup(par2, (MixinCelestialUniforms)(Object) new CelestialUniforms(this.sunPathRotation));

        List<VGImage> outImgs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            outImgs.add(((IRenderTargetVkGetter)renderTargets.getOrCreate(i)).getMain());
        }
        pipeline.execute(outImgs);
    }

    @Inject(method = "destroyShaders", at = @At("TAIL"))
    private void destory(CallbackInfo ci) {
        ctx.cmd.waitQueueIdle(0);
        pipeline.destory();
        pipeline = null;
    }
}
