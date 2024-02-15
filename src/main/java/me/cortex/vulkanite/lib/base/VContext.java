package me.cortex.vulkanite.lib.base;

import me.cortex.vulkanite.lib.cmd.CommandManager;
import me.cortex.vulkanite.lib.other.sync.SyncManager;
import me.cortex.vulkanite.lib.memory.MemoryManager;
import org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT;
import static org.lwjgl.vulkan.VK10.VK_OBJECT_TYPE_UNKNOWN;

public class VContext {
    public final VkDevice device;


    public final MemoryManager memory;
    public final SyncManager sync;
    public final CommandManager cmd;
    public final DeviceProperties properties;
    public final boolean hasDebugUtils;
    public VContext(VkDevice device, int queueCount, boolean hasDeviceAddresses, boolean hasDebugUtils) {
        this.device = device;
        memory = new MemoryManager(device, hasDeviceAddresses);
        sync = new SyncManager(device);
        cmd = new CommandManager(device, queueCount);
        properties = new DeviceProperties(device);
        this.hasDebugUtils = hasDebugUtils;
    }

    public void setDebugUtilsObjectName(long handle, int objectType, String name) {
        if (hasDebugUtils) {
            try (var stack = stackPush()) {
                vkSetDebugUtilsObjectNameEXT(device, VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                        .sType$Default()
                        .objectType(objectType)
                        .objectHandle(handle)
                        .pObjectName(memUTF8(name)));
            }
        }
    }
}
