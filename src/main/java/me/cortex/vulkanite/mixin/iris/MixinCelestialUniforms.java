package me.cortex.vulkanite.mixin.iris;

import net.coderbot.iris.uniforms.CelestialUniforms;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = CelestialUniforms.class, remap = false)
public interface MixinCelestialUniforms {
    @Invoker("getSunPosition")
    public Vector4f invokeGetSunPosition();

    @Invoker("getMoonPosition")
    public Vector4f invokeGetMoonPosition();
}
