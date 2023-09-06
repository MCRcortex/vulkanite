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
            builder.add("raygen_"+i+".glsl");
            for (int j = 0; j < 4; j++) {
                builder.add("raymiss_"+i+"_"+j+".glsl");
                builder.add("rayclose_"+i+"_"+j+".glsl");
                builder.add("rayany_"+i+"_"+j+".glsl");
                builder.add("rayintersect_"+i+"_"+j+".glsl");
            }
        }
        cir.setReturnValue(builder.build());
    }
}
