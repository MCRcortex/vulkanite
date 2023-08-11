package me.cortex.vulkanite.compat;

import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;

import java.util.Map;

public interface IAccelerationBuildResult {
    void setAccelerationGeometryData(Map<TerrainRenderPass, NativeBuffer> map);
    Map<TerrainRenderPass, NativeBuffer> getAccelerationGeometryData();
}
