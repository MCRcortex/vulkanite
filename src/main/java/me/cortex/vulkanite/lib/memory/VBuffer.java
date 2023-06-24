package me.cortex.vulkanite.lib.memory;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceOrHostAddressKHR;
import org.lwjgl.vulkan.VkMappedMemoryRange;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.vkGetBufferDeviceAddressKHR;
import static org.lwjgl.vulkan.VK10.vkFlushMappedMemoryRanges;

public class VBuffer {
    private final VmaAllocator.BufferAllocation allocation;
    VBuffer(VmaAllocator.BufferAllocation allocation) {
        this.allocation = allocation;
    }

    public long buffer() {
        return allocation.buffer;
    }

    public void free() {
        allocation.free();
    }

    public long deviceAddress() {
        return allocation.deviceAddress;
    }

    public void flush(long offset, long size) {
        allocation.flush(offset, size);
    }
}
