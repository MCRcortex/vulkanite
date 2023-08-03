package me.cortex.vulkanite.lib.other.sync;

import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.vulkan.VK10.vkDestroySemaphore;

public record VSemaphore(VkDevice device, long semaphore) implements Pointer {
    @Override
    public long address() {
        return semaphore;
    }

    public void free() {
        vkDestroySemaphore(device, semaphore, null);
    }

}
