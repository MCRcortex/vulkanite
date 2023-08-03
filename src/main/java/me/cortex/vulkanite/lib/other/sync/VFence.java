package me.cortex.vulkanite.lib.other.sync;

import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.vulkan.VK10.vkDestroyFence;

public record VFence(VkDevice device, long fence) implements Pointer {
    @Override
    public long address() {
        return fence;
    }

    public void free() {
        vkDestroyFence(device, fence, null);
    }
}
