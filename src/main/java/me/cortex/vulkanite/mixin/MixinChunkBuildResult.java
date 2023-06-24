package me.cortex.vulkanite.mixin;

import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkBuildResult.class)
public class MixinChunkBuildResult implements IAccelerationBuildResult {

}
