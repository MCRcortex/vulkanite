package me.cortex.vulkanite.lib.other.sync;

import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VRef;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkDevice;

import java.util.Objects;

import static org.lwjgl.vulkan.VK10.vkDestroyFence;

public final class VFence extends VObject implements Pointer {
    private final VkDevice device;
    private final long fence;

    private VFence(VkDevice device, long fence) {
        this.device = device;
        this.fence = fence;
    }

    static VRef<VFence> create(VkDevice device, long fence) {
        return new VRef<>(new VFence(device, fence));
    }

    @Override
    public long address() {
        return fence;
    }

    @Override
    protected void free() {
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
