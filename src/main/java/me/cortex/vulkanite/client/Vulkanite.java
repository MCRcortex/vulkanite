package me.cortex.vulkanite.client;

import me.cortex.vulkanite.acceleration.AccelerationManager;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;

public class Vulkanite {
    public static boolean IS_ENABLED = true;
    public static Vulkanite INSTANCE;

    private AccelerationManager accelerationManager;

    public void upload(ChunkBuildResult result) {
        accelerationManager.chunkBuild(result);
    }
}
