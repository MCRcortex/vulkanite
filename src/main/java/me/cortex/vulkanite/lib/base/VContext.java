package me.cortex.vulkanite.lib.base;

import me.cortex.vulkanite.lib.cmd.CommandManager;
import me.cortex.vulkanite.lib.other.sync.SyncManager;
import me.cortex.vulkanite.lib.memory.MemoryManager;
import org.lwjgl.vulkan.VkDevice;

public class VContext {
    public final VkDevice device;


    public final MemoryManager memory;
    public final SyncManager sync;
    public final CommandManager cmd;
    public final DeviceProperties properties;
    public VContext(VkDevice device, int queueCount, boolean hasDeviceAddresses) {
        this.device = device;
        memory = new MemoryManager(device, hasDeviceAddresses);
        sync = new SyncManager(device);
        cmd = new CommandManager(device, queueCount);
        properties = new DeviceProperties(device);
    }
}
