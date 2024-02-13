package me.cortex.vulkanite.lib.memory;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VRef;
import org.lwjgl.vulkan.VkDeviceOrHostAddressConstKHR;

import static org.lwjgl.vulkan.VK10.VK_OBJECT_TYPE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_WHOLE_SIZE;

public class VBuffer extends VObject {
    private VmaAllocator.BufferAllocation allocation;
    private final VkDeviceOrHostAddressConstKHR deviceAddressConst;

    public static VRef<VBuffer> create(VmaAllocator.BufferAllocation allocation) {
        return new VRef<>(new VBuffer(allocation));
    }

    protected VBuffer(VmaAllocator.BufferAllocation allocation) {
        this.allocation = allocation;
        if (allocation.deviceAddress != -1) {
            this.deviceAddressConst = VkDeviceOrHostAddressConstKHR.calloc()
                    .deviceAddress(allocation.deviceAddress);
        } else {
            this.deviceAddressConst = null;
        }
    }

    public long buffer() {
        return allocation.buffer;
    }

    protected void free() {
        allocation.free();
        allocation = null;
    }

    public long deviceAddress() {
        if (allocation.deviceAddress == -1)
            throw new IllegalStateException();
        return allocation.deviceAddress;
    }

    public VkDeviceOrHostAddressConstKHR deviceAddressConst() {
        if (allocation.deviceAddress == -1)
            throw new IllegalStateException();
        return deviceAddressConst;
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

    public void setDebugUtilsObjectName(String name) {
        Vulkanite.INSTANCE.getCtx().setDebugUtilsObjectName(buffer(), VK_OBJECT_TYPE_BUFFER, name);
    }
}
