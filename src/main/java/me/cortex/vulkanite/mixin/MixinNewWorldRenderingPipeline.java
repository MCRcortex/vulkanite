package me.cortex.vulkanite.mixin;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IRenderTargetVkGetter;
import net.coderbot.iris.mixin.LevelRendererAccessor;
import net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NewWorldRenderingPipeline.class, remap = false)
public class MixinNewWorldRenderingPipeline {
    @Shadow @Final private RenderTargets renderTargets;

    @Inject(method = "renderShadows", at = @At("TAIL"))
    private void renderShadows(LevelRendererAccessor par1, Camera par2, CallbackInfo ci) {
        Vulkanite.INSTANCE.pipeline.renderPostShadows(((IRenderTargetVkGetter)renderTargets.get(0)).getMain(), par2);
    }
}
