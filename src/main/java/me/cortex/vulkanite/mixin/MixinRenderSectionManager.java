package me.cortex.vulkanite.mixin;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import me.cortex.vulkanite.client.Vulkanite;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public class MixinRenderSectionManager {
    @Shadow @Final private Long2ReferenceMap<RenderSection> sections;

    @Inject(method = "destroy", at = @At("HEAD"))
    private void onDestroy(CallbackInfo ci) {
        for (var section : sections.values()) {
            Vulkanite.INSTANCE.sectionRemove(section);
        }
    }
}
