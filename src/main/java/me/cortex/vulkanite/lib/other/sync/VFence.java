package me.cortex.vulkanite.lib.other.sync;

import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkDevice;

import java.util.Objects;

import static org.lwjgl.vulkan.VK10.vkDestroyFence;

public final class VFence extends TrackedResourceObject implements Pointer {
    private final VkDevice device;
    private final long fence;

    public VFence(VkDevice device, long fence) {
        this.device = device;
        this.fence = fence;
    }

    @Override
    public long address() {
        return fence;
    }

    public void free() {
        free0();
        vkDestroyFence(device, fence, null);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (VFence) obj;
        return Objects.equals(this.device, that.device) &&
                this.fence == that.fence;
    }

    @Override
    public int hashCode() {
        return Objects.hash(device, fence);
    }
}
