package me.cortex.vulkanite.mixin.minecraft;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IVGImage;
import me.cortex.vulkanite.lib.memory.VGImage;
import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTexture.class)
public class MixinAbstractTexture implements IVGImage {
    @Shadow protected int glId;
    @Unique private VGImage vgImage;

    @Override
    public void setVGImage(VGImage image) {
        this.vgImage = image;
    }

    @Override
    public VGImage getVGImage() {
        return vgImage;
    }

    @Inject(method = "getGlId", at = @At("HEAD"), cancellable = true)
    private void redirectGetId(CallbackInfoReturnable<Integer> cir) {
        if (vgImage != null) {
            if (glId != -1) {
                throw new IllegalStateException("glId != -1 while VGImage is set");
            }
            cir.setReturnValue(vgImage.glId);
            cir.cancel();
        }
    }

    @Inject(method = "clearGlId", at = @At("HEAD"), cancellable = true)
    private void redirectClear(CallbackInfo ci) {
        if (vgImage != null) {
            Vulkanite.INSTANCE.addSyncedCallback(vgImage::free);
            ci.cancel();
        }
    }
}
