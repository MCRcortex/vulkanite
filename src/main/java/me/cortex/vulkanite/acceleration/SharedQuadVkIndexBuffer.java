package me.cortex.vulkanite.acceleration;

import org.lwjgl.vulkan.VkDeviceOrHostAddressConstKHR;
import org.lwjgl.vulkan.VkDeviceOrHostAddressKHR;

import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;

public class SharedQuadVkIndexBuffer {
    public static final int TYPE = VK_INDEX_TYPE_UINT32;
    public static VkDeviceOrHostAddressConstKHR getIndexBuffer(int quadCount) {
        return null;
    }
}
