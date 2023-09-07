package me.cortex.vulkanite.mixin.iris;

import com.google.common.collect.ImmutableList;
import net.coderbot.iris.shaderpack.include.ShaderPackSourceNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShaderPackSourceNames.class, remap = false)
public class MixinShaderPackSourceNames {
    @Inject(method = "findPotentialStarts", at = @At("RETURN"), cancellable = true)
    private static void injectRaytraceShaderNames(CallbackInfoReturnable<ImmutableList<String>> cir) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.addAll(cir.getReturnValue());
        for (int i = 0; i < 3; i++) {
            builder.add("ray"+i+".rgen");
            for (int j = 0; j < 4; j++) {
                builder.add("ray"+i+"_"+j+".rmiss");
                builder.add("ray"+i+"_"+j+".rchit");
                builder.add("ray"+i+"_"+j+".rahit");
                builder.add("ray"+i+"_"+j+".rint");
            }
        }
        cir.setReturnValue(builder.build());
    }
}
