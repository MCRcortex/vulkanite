package me.cortex.vulkanite.lib.memory;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceOrHostAddressKHR;
import org.lwjgl.vulkan.VkMappedMemoryRange;

import java.lang.ref.Cleaner;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.vkGetBufferDeviceAddressKHR;
import static org.lwjgl.vulkan.VK10.VK_WHOLE_SIZE;
import static org.lwjgl.vulkan.VK10.vkFlushMappedMemoryRanges;

public class VBuffer {
    private VmaAllocator.BufferAllocation allocation;

    VBuffer(VmaAllocator.BufferAllocation allocation) {
        this.allocation = allocation;
    }

    public long buffer() {
        return allocation.buffer;
    }

    public void free() {
        allocation.free();
        allocation = null;
    }

    public long deviceAddress() {
        if (allocation.deviceAddress == -1)
            throw new IllegalStateException();
        return allocation.deviceAddress;
    }

    public long map() {
        return allocation.map();
    }

    public void unmap() {
        allocation.unmap();
    }

    public void flush() {
        allocation.flush(0, VK_WHOLE_SIZE);
    }

    public void flush(long offset, long size) {
        allocation.flush(offset, size);
    }

    public long size() {
        return allocation.size();
    }
}
