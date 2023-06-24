package me.cortex.vulkanite.lib.memory;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAccelerationStructureDeviceAddressInfoKHR;
import org.lwjgl.vulkan.VkDevice;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;

public class VAccelerationStructure {
    public final long structure;
    public final VBuffer buffer;
    public final long deviceAddress;
    private final VkDevice device;
    VAccelerationStructure(VkDevice device, long structure, VBuffer buffer) {
        this.device = device;
        this.buffer = buffer;
        this.structure = structure;
        try (MemoryStack stack = MemoryStack.stackPush()){
            deviceAddress = vkGetAccelerationStructureDeviceAddressKHR(device,
                    VkAccelerationStructureDeviceAddressInfoKHR
                            .calloc(stack)
                            .sType$Default()
                            .accelerationStructure(structure));
        }
    }

    public void free() {
        vkDestroyAccelerationStructureKHR(device, structure, null);
        buffer.free();
    }
}
