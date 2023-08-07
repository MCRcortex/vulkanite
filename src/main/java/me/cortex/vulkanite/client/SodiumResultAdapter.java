package me.cortex.vulkanite.client;

import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

//TODO: FIXME! the native buffer is destroyed by the AccelerationBlasBuilder after its copied to the gpu, however
// on world reload or for whatever reason that the result is destroyed (and not submitted to the blas builder)
// must find a way to free the native buffers
public class SodiumResultAdapter {
    public static void compute(ChunkBuildOutput buildResult) {
        var ebr = (IAccelerationBuildResult) buildResult;
        Map<TerrainRenderPass, NativeBuffer> map = new HashMap<>();
        ebr.setAccelerationGeometryData(map);
        for (var pass : buildResult.meshes.entrySet()) {
            var vertData = pass.getValue().getVertexData();

            int stride = 20;//TODO: dont hardcode this

            if (vertData.getLength()%stride != 0)
                throw new IllegalStateException("Mismatch length and stride");
            int vertices = vertData.getLength()/stride;
            if (vertices % 4 != 0)
                throw new IllegalStateException("Non multiple 4 vertex count");
            NativeBuffer geometryBuffer = new NativeBuffer(vertices*(4*3));
            long addr = MemoryUtil.memAddress(geometryBuffer.getDirectBuffer());
            long srcVert = MemoryUtil.memAddress(vertData.getDirectBuffer());
            for (var faceData : pass.getValue().getVertexRanges()) {
                if (faceData == null) continue;
                for (int i = 0; i < faceData.vertexCount(); i++) {
                    long base = srcVert + (long) stride * (i + faceData.vertexStart());
                    float x = decodePosition(MemoryUtil.memGetShort(base));
                    float y = decodePosition(MemoryUtil.memGetShort(base + 2));
                    float z = decodePosition(MemoryUtil.memGetShort(base + 4));
                    MemoryUtil.memPutFloat(addr, x);
                    MemoryUtil.memPutFloat(addr + 4, y);
                    MemoryUtil.memPutFloat(addr + 8, z);
                    addr += 12;
                }
            }
            map.put(pass.getKey(), geometryBuffer);
        }
    }


    private static float decodePosition(short v) {
        return Short.toUnsignedInt(v)*(1f/2048.0f)-8.0f;
    }
}
