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
        ebr.setAccelerationGeometryData(map);
        for (var pass : buildResult.meshes.entrySet()) {
            var vertData = pass.getValue().getVertexData();

            int stride = ebr.getVertexFormat().getVertexFormat().getStride();

            if (vertData.getLength()%stride != 0)
                throw new IllegalStateException("Mismatch length and stride");
            int vertices = vertData.getLength()/stride;
            if (vertices % 4 != 0)
                throw new IllegalStateException("Non multiple 4 vertex count");

            NativeBuffer geometryBuffer = new NativeBuffer(vertices*(2*3));
            long addr = MemoryUtil.memAddress(geometryBuffer.getDirectBuffer());
            long srcVert = MemoryUtil.memAddress(vertData.getDirectBuffer());

            for (var faceData : pass.getValue().getVertexRanges()) {
                if (faceData == null) continue;
                for (int i = 0; i < faceData.vertexCount(); i++) {
                    long base = srcVert + (long) stride * (i + faceData.vertexStart());
                    float x = decodePosition(MemoryUtil.memGetShort(base));
                    float y = decodePosition(MemoryUtil.memGetShort(base + 2));
                    float z = decodePosition(MemoryUtil.memGetShort(base + 4));

                    MemoryUtil.memPutShort(addr, (short) x);
                    MemoryUtil.memPutShort(addr + 2, (short) y);
                    MemoryUtil.memPutShort(addr + 4, (short) z);
                    addr += 6;
                }
            }
            map.put(pass.getKey(), new GeometryData(vertices>>2, geometryBuffer));
        }
    }


    private static float decodePosition(short v) {
        return Short.toUnsignedInt(v)*(1f/2048.0f)-8.0f;
    }
}
