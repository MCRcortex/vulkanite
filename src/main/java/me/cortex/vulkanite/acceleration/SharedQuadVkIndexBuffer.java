package me.cortex.vulkanite.acceleration;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.memory.VBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDeviceOrHostAddressConstKHR;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;

public class SharedQuadVkIndexBuffer {
    public static final int TYPE = VK_INDEX_TYPE_UINT32;
    private static VBuffer indexBuffer = null;
    private static VkDeviceOrHostAddressConstKHR indexBufferAddr = null;
    private static int currentQuadCount = 0;

    public synchronized static VkDeviceOrHostAddressConstKHR getIndexBuffer(VContext context, VCmdBuff uploaCmdBuff, int quadCount) {
        if (currentQuadCount < quadCount) {
            makeNewIndexBuffer(context, uploaCmdBuff, quadCount);
        }

        return indexBufferAddr;
    }

    private static void makeNewIndexBuffer(VContext context, VCmdBuff uploaCmdBuff, int quadCount) {
        if (indexBuffer != null) {
            //TODO: need to enqueue the old indexBuffer for memory release
            indexBufferAddr.free();//Note this is calloced (in global heap) so need to release it IS SEPERATE FROM indexBuffer
            throw new IllegalStateException();
        }

        ByteBuffer buffer = genQuadIdxs(quadCount);
        //TODO: dont harcode VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR and VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT
        indexBuffer = context.memory.createBuffer(buffer.remaining(),
                VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT
                        | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR
                        | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                VK_MEMORY_HEAP_DEVICE_LOCAL_BIT);
        indexBuffer.setDebugUtilsObjectName("Geometry Index Buffer");

        uploaCmdBuff.encodeDataUpload(context.memory, MemoryUtil.memAddress(buffer), indexBuffer, 0,
                buffer.remaining());

        indexBufferAddr = VkDeviceOrHostAddressConstKHR.calloc().deviceAddress(indexBuffer.deviceAddress());
        currentQuadCount = quadCount;
    }

    public static ByteBuffer genQuadIdxs(int quadCount) {
        //short[] idxs = {0, 1, 2, 0, 2, 3};

        int vertexCount = quadCount * 4;
        int indexCount = vertexCount * 3 / 2;
        ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * Integer.BYTES);
        IntBuffer idxs = buffer.asIntBuffer();
        //short[] idxs = new short[indexCount];

        int j = 0;
        for(int i = 0; i < vertexCount; i += 4) {

            idxs.put(j, i);
            idxs.put(j + 1, (i + 1));
            idxs.put(j + 2, (i + 2));
            idxs.put(j + 3, (i));
            idxs.put(j + 4, (i + 2));
            idxs.put(j + 5, (i + 3));

            j += 6;
        }

        return buffer;
    }
}
