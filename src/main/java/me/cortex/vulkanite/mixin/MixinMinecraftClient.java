package me.cortex.vulkanite.mixin;

import me.cortex.vulkanite.client.Vulkanite;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderTick(boolean tick, CallbackInfo ci) {
        Vulkanite.INSTANCE.renderTick();
    }
}
