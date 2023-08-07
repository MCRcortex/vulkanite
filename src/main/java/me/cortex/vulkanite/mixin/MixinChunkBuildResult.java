package me.cortex.vulkanite.mixin;

import me.cortex.vulkanite.client.IAccelerationBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import java.util.Map;

@Mixin(value = ChunkBuildOutput.class, remap = false)
public class MixinChunkBuildResult implements IAccelerationBuildResult {
    @Unique private Map<TerrainRenderPass, NativeBuffer> geometryMap;

    @Override
    public void setAccelerationGeometryData(Map<TerrainRenderPass, NativeBuffer> map) {
        this.geometryMap = map;
    }

    @Override
    public Map<TerrainRenderPass, NativeBuffer> getAccelerationGeometryData() {
        return geometryMap;
    }
}
