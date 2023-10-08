package me.cortex.vulkanite.compat;

import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

import java.util.HashMap;
import java.util.Map;

//TODO: FIXME! the native buffer is destroyed by the AccelerationBlasBuilder after its copied to the gpu, however
// on world reload or for whatever reason that the result is destroyed (and not submitted to the blas builder)
// must find a way to free the native buffers
public class SodiumResultAdapter {
    public static void compute(ChunkBuildOutput buildResult) {
        var ebr = (IAccelerationBuildResult) buildResult;
        Map<TerrainRenderPass, GeometryData> map = new HashMap<>();
        for (var pass : buildResult.meshes.entrySet()) {
            var vertData = pass.getValue().getVertexData();

            int stride = ebr.getVertexFormat().getVertexFormat().getStride();

            if (vertData.getLength()%stride != 0)
                throw new IllegalStateException("Mismatch length and stride");
            int vertices = vertData.getLength()/stride;
            if (vertices % 4 != 0)
                throw new IllegalStateException("Non multiple 4 vertex count");

            map.put(pass.getKey(), new GeometryData(vertices>>2));
        }

        if (!map.isEmpty()) {
            ebr.setAccelerationGeometryData(map);
        } else {
            ebr.setAccelerationGeometryData(null);
        }
    }
}
