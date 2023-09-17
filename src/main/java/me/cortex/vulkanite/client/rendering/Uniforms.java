package me.cortex.vulkanite.client.rendering;

import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Objects;

public class Uniforms {


    //Copied from CommonUniforms
    static int isEyeInWater() {
        CameraSubmersionType var0 = MinecraftClient.getInstance().gameRenderer.getCamera().getSubmersionType();
        if (var0 == CameraSubmersionType.WATER) {
            return 1;
        } else if (var0 == CameraSubmersionType.LAVA) {
            return 2;
        } else {
            return var0 == CameraSubmersionType.POWDER_SNOW ? 3 : 0;
        }
    }

    //Copied from CelestialUniforms

    static Vector4f getSunPosition() {
        return getCelestialPosition(100.0F);
    }

    static Vector4f getMoonPosition() {
        return getCelestialPosition(-100.0F);
    }


    static Vector4f getCelestialPosition(float y) {
        final float sunPathRotation = 0.0f;

        Vector4f position = new Vector4f(0.0F, y, 0.0F, 0.0F);

        Matrix4f celestial = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());

        // This is the same transformation applied by renderSky, however, it's been moved to here.
        // This is because we need the result of it before it's actually performed in vanilla.
        celestial.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
        celestial.rotate(RotationAxis.POSITIVE_Z.rotationDegrees(sunPathRotation));
        celestial.rotate(RotationAxis.POSITIVE_X.rotationDegrees(getSkyAngle() * 360.0F));

        position = celestial.transform(position);

        return position;
    }

    private static ClientWorld getWorld() {
        return MinecraftClient.getInstance().world;
    }

    private static float getSkyAngle() {
        return getWorld().getSkyAngle(CapturedRenderingState.INSTANCE.getTickDelta());
    }
}
