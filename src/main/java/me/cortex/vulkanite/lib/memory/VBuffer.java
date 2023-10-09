package me.cortex.vulkanite.lib.memory;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.base.TrackedResourceObject;

import static org.lwjgl.vulkan.VK10.VK_OBJECT_TYPE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_WHOLE_SIZE;

public class VBuffer extends TrackedResourceObject {
    private VmaAllocator.BufferAllocation allocation;

    VBuffer(VmaAllocator.BufferAllocation allocation) {
        this.allocation = allocation;
    }

    public long buffer() {
        return allocation.buffer;
    }

    public void free() {
        free0();
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

    public void setDebugUtilsObjectName(String name) {
        Vulkanite.INSTANCE.getCtx().setDebugUtilsObjectName(buffer(), VK_OBJECT_TYPE_BUFFER, name);
    }
}
