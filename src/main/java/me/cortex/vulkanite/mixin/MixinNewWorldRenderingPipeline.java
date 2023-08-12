package me.cortex.vulkanite.mixin;

import me.cortex.vulkanite.client.Vulkanite;
import net.coderbot.iris.mixin.LevelRendererAccessor;
import net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NewWorldRenderingPipeline.class, remap = false)
public class MixinNewWorldRenderingPipeline {
    @Inject(method = "renderShadows", at = @At("TAIL"))
    private void renderShadows(LevelRendererAccessor par1, Camera par2, CallbackInfo ci) {
        Vulkanite.INSTANCE.pipeline.renderPostShadows();
    }
}
