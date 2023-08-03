package me.cortex.vulkanite.client;

import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

import java.util.EnumMap;
import java.util.Map;

//TODO: FIXME! the native buffer is destroyed by the AccelerationBlasBuilder after its copied to the gpu, however
// on world reload or for whatever reason that the result is destroyed (and not submitted to the blas builder)
// must find a way to free the native buffers
public class SodiumResultAdapter {
    public static void compute(ChunkBuildResult buildResult) {
        var ebr = (IAccelerationBuildResult) buildResult;
        Map<BlockRenderPass, NativeBuffer> map = new EnumMap<>(BlockRenderPass.class);
        ebr.setAccelerationGeometryData(map);
        for (var pass : buildResult.meshes.entrySet()) {
            var vertData = pass.getValue().getVertexData();
            int stride = vertData.vertexFormat().getStride();
            if (vertData.vertexBuffer().getLength()%vertData.vertexFormat().getStride() != 0)
                throw new IllegalStateException("Mismatch length and stride");
            int vertices = vertData.vertexBuffer().getLength()/vertData.vertexFormat().getStride();
            if (vertices % 4 != 0)
                throw new IllegalStateException("Non multiple 4 vertex count");
            NativeBuffer geometryBuffer = new NativeBuffer(vertices*(4*3));
            long addr = MemoryUtil.memAddress(geometryBuffer.getDirectBuffer());
            for (var faceData : pass.getValue().getParts().entrySet()) {
                long srcVert = MemoryUtil.memAddress(vertData.vertexBuffer().getDirectBuffer());
                long srcIdx = faceData.getValue().elementPointer() + MemoryUtil.memAddress(vertData.indexBuffer().getDirectBuffer());
                for (int i = 0; i < (faceData.getValue().elementCount()/6); i++) {
                    long base = srcVert + (long) MemoryUtil.memGetInt(srcIdx + 4 * 6 * i) * stride;
                    for (int j = 0; j < 4; j++) {
                        float x = decodePosition(MemoryUtil.memGetShort(base));
                        float y = decodePosition(MemoryUtil.memGetShort(base + 2));
                        float z = decodePosition(MemoryUtil.memGetShort(base + 4));
                        MemoryUtil.memPutFloat(addr, x);
                        MemoryUtil.memPutFloat(addr + 4, y);
                        MemoryUtil.memPutFloat(addr + 8, z);
                        addr += 12;
                        base += stride;
                    }
                }
            }
            map.put(pass.getKey(), geometryBuffer);
        }
    }


    private static float decodePosition(short v) {
        return Short.toUnsignedInt(v)*(1f/2048.0f)-8.0f;
    }
}
