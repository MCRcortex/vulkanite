package me.cortex.vulkanite.mixin;

import me.cortex.vulkanite.client.IAccelerationBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import java.util.Map;

@Mixin(ChunkBuildResult.class)
public class MixinChunkBuildResult implements IAccelerationBuildResult {
    @Unique private Map<BlockRenderPass, NativeBuffer> geometryMap;

    @Override
    public void setAccelerationGeometryData(Map<BlockRenderPass, NativeBuffer> map) {
        this.geometryMap = map;
    }

    @Override
    public Map<BlockRenderPass, NativeBuffer> getAccelerationGeometryData() {
        return geometryMap;
    }
}
