package me.cortex.vulkanite.mixin.iris;

import net.coderbot.iris.uniforms.CommonUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = CommonUniforms.class, remap = false)
public interface MixinCommonUniforms {
    @Invoker("isEyeInWater")
    public static int invokeIsEyeInWater() { return 0; } // Must have a body, but this is just a dummy body and doesn't actually get executed.
}
