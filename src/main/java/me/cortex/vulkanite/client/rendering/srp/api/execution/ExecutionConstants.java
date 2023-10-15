package me.cortex.vulkanite.client.rendering.srp.api.execution;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.other.sync.VFence;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector2i;
import org.joml.Vector3i;

public class ExecutionConstants {
    public static ExecutionConstants INSTANCE = new ExecutionConstants();

    public Vector2i getScreenSize() {
        return new Vector2i(MinecraftClient.getInstance().getFramebuffer().textureWidth, MinecraftClient.getInstance().getFramebuffer().textureHeight);
    }
}
