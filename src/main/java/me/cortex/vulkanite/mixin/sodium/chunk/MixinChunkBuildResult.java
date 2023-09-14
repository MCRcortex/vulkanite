package me.cortex.vulkanite.mixin.sodium.chunk;

import me.cortex.vulkanite.compat.GeometryData;
import me.cortex.vulkanite.compat.IAccelerationBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(value = ChunkBuildOutput.class, remap = false)
public class MixinChunkBuildResult implements IAccelerationBuildResult {
    @Unique private Map<TerrainRenderPass, GeometryData> geometryMap;
    @Unique private ChunkVertexType vertexType;

    @Override
    public void setAccelerationGeometryData(Map<TerrainRenderPass, GeometryData> map) {
        this.geometryMap = map;
    }

    @Override
    public Map<TerrainRenderPass, GeometryData> getAccelerationGeometryData() {
        return geometryMap;
    }

    @Override
    public ChunkVertexType getVertexFormat() {
        return vertexType;
    }

    @Override
    public void setVertexFormat(ChunkVertexType format) {
        vertexType = format;
    }
}
