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

                    MemoryUtil.memPutShort(addr, (short) fromFloat(x));
                    MemoryUtil.memPutShort(addr + 2, (short) fromFloat(y));
                    MemoryUtil.memPutShort(addr + 4, (short) fromFloat(z));
                    addr += 6;
                }
            }
            map.put(pass.getKey(), new GeometryData(vertices>>2, geometryBuffer));
        }

        if (!map.isEmpty()) {
            ebr.setAccelerationGeometryData(map);
        } else {
            ebr.setAccelerationGeometryData(null);
        }
    }


    private static float decodePosition(short v) {
        return Short.toUnsignedInt(v)*(1f/2048.0f)-8.0f;
    }

    public static int fromFloat( float fval )
    {
        int fbits = Float.floatToIntBits( fval );
        int sign = fbits >>> 16 & 0x8000;          // sign only
        int val = ( fbits & 0x7fffffff ) + 0x1000; // rounded value

        if( val >= 0x47800000 )               // might be or become NaN/Inf
        {                                     // avoid Inf due to rounding
            if( ( fbits & 0x7fffffff ) >= 0x47800000 )
            {                                 // is or must become NaN/Inf
                if( val < 0x7f800000 )        // was value but too large
                    return sign | 0x7c00;     // make it +/-Inf
                return sign | 0x7c00 |        // remains +/-Inf or NaN
                        ( fbits & 0x007fffff ) >>> 13; // keep NaN (and Inf) bits
            }
            return sign | 0x7bff;             // unrounded not quite Inf
        }
        if( val >= 0x38800000 )               // remains normalized value
            return sign | val - 0x38000000 >>> 13; // exp - 127 + 15
        if( val < 0x33000000 )                // too small for subnormal
            return sign;                      // becomes +/-0
        val = ( fbits & 0x7fffffff ) >>> 23;  // tmp exp for subnormal calc
        return sign | ( ( fbits & 0x7fffff | 0x800000 ) // add subnormal bit
                + ( 0x800000 >>> val - 102 )     // round depending on cut off
                >>> 126 - val );   // div by 2^(1-(exp-127+15)) and >> 13 | exp=0
    }
}
