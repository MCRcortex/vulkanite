package me.cortex.vulkanite.client;

import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;

import java.util.Map;

public interface IAccelerationBuildResult {
    void setAccelerationGeometryData(Map<BlockRenderPass, NativeBuffer> map);
    Map<BlockRenderPass, NativeBuffer> getAccelerationGeometryData();
}
