package me.cortex.vulkanite.mixin.iris;

import net.coderbot.iris.gl.shader.StandardMacros;
import net.coderbot.iris.shaderpack.StringPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;

@Mixin(value = StandardMacros.class, remap = false)
public class MixinStandardMacros {
    @Inject(method = "createStandardEnvironmentDefines", at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/gl/shader/StandardMacros;define(Ljava/util/List;Ljava/lang/String;)V", ordinal = 0), locals = LocalCapture.CAPTURE_FAILHARD)
    private static void injectVulkaniteDefine(CallbackInfoReturnable<Iterable<StringPair>> cir, ArrayList<StringPair> defines) {
        defines.add(new StringPair("VULKANITE", " "));
    }
}
