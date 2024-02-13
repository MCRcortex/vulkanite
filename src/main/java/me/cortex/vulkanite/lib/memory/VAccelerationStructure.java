package me.cortex.vulkanite.lib.memory;

import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VRef;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAccelerationStructureDeviceAddressInfoKHR;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.vulkan.KHRAccelerationStructure.*;

public class VAccelerationStructure extends VObject {
    public final long structure;
    @SuppressWarnings("FieldCanBeLocal")
    private final VRef<VBuffer> buffer;

    public final long deviceAddress;
    private final VkDevice device;

    protected VAccelerationStructure(VkDevice device, long structure, final VRef<VBuffer> buffer) {
        this.device = device;
        this.buffer = buffer.addRef();
        this.structure = structure;
        try (MemoryStack stack = MemoryStack.stackPush()){
            deviceAddress = vkGetAccelerationStructureDeviceAddressKHR(device,
                    VkAccelerationStructureDeviceAddressInfoKHR
                            .calloc(stack)
                            .sType$Default()
                            .accelerationStructure(structure));
        }
    }

    public static VRef<VAccelerationStructure> create(VkDevice device, long structure, VRef<VBuffer> buffer) {
        return new VRef<>(new VAccelerationStructure(device, structure, buffer));
    }

    public void free() {
        vkDestroyAccelerationStructureKHR(device, structure, null);
    }
}
