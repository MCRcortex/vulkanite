package me.cortex.vulkanite.mixin.sodium;

import me.cortex.vulkanite.client.Vulkanite;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSection.class, remap = false)
public class MixinRenderSection {
    @Inject(method = "delete", at = @At("HEAD"))
    private void onSectionDelete(CallbackInfo ci) {
        Vulkanite.INSTANCE.sectionRemove((RenderSection)(Object)this);
    }
}
