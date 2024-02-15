package me.cortex.vulkanite.lib.other.sync;

import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VRef;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkDevice;

import java.util.Objects;

import static org.lwjgl.vulkan.VK10.vkDestroySemaphore;

public class VSemaphore extends VObject implements Pointer {
    private final VkDevice device;
    private final long semaphore;

    protected VSemaphore(VkDevice device, long semaphore) {
        this.device = device;
        this.semaphore = semaphore;
    }

    public static VRef<VSemaphore> create(VkDevice device, long semaphore) {
        return new VRef<>(new VSemaphore(device, semaphore));
    }

    @Override
    public long address() {
        return semaphore;
    }

    protected void free() {
        vkDestroySemaphore(device, semaphore, null);
    }

    public VkDevice device() {
        return device;
    }

    public long semaphore() {
        return semaphore;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (VSemaphore) obj;
        return Objects.equals(this.device, that.device) &&
                this.semaphore == that.semaphore;
    }

    @Override
    public int hashCode() {
        return Objects.hash(device, semaphore);
    }

    @Override
    public String toString() {
        return "VSemaphore[" +
                "device=" + device + ", " +
                "semaphore=" + semaphore + ']';
    }


}
