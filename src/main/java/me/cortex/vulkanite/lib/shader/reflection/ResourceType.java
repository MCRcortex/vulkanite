package me.cortex.vulkanite.lib.shader.reflection;

import static org.lwjgl.util.spvc.Spvc.*;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.VK10.*;

public enum ResourceType {
    UNIFORM_BUFFER(SPVC_RESOURCE_TYPE_UNIFORM_BUFFER),
    STORAGE_BUFFER(SPVC_RESOURCE_TYPE_STORAGE_BUFFER),
    STAGE_INPUT(SPVC_RESOURCE_TYPE_STAGE_INPUT),
    STAGE_OUTPUT(SPVC_RESOURCE_TYPE_STAGE_OUTPUT),
    SUBPASS_INPUT(SPVC_RESOURCE_TYPE_SUBPASS_INPUT),
    STORAGE_IMAGE(SPVC_RESOURCE_TYPE_STORAGE_IMAGE),
    SAMPLED_IMAGE(SPVC_RESOURCE_TYPE_SAMPLED_IMAGE),
    ATOMIC_COUNTER(SPVC_RESOURCE_TYPE_ATOMIC_COUNTER),
    PUSH_CONSTANT(SPVC_RESOURCE_TYPE_PUSH_CONSTANT),
    SEPARATE_IMAGE(SPVC_RESOURCE_TYPE_SEPARATE_IMAGE),
    SEPARATE_SAMPLERS(SPVC_RESOURCE_TYPE_SEPARATE_SAMPLERS),
    ACCELERATION_STRUCTURE(SPVC_RESOURCE_TYPE_ACCELERATION_STRUCTURE);
    public final int id;

    ResourceType(int id) {
        this.id = id;
    }

    public int toVkDescriptorType() {
        switch (this.id) {
            case SPVC_RESOURCE_TYPE_UNIFORM_BUFFER:
                return VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
            case SPVC_RESOURCE_TYPE_STORAGE_BUFFER:
                return VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            case SPVC_RESOURCE_TYPE_STAGE_INPUT:
                return -1;
            case SPVC_RESOURCE_TYPE_STAGE_OUTPUT:
                return -1;
            case SPVC_RESOURCE_TYPE_SUBPASS_INPUT:
                return VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT;
            case SPVC_RESOURCE_TYPE_STORAGE_IMAGE:
                return VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
            case SPVC_RESOURCE_TYPE_SAMPLED_IMAGE:
                return VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            case SPVC_RESOURCE_TYPE_ATOMIC_COUNTER:
                return -1;
            case SPVC_RESOURCE_TYPE_PUSH_CONSTANT:
                return -1;
            case SPVC_RESOURCE_TYPE_SEPARATE_IMAGE:
                return VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE;
            case SPVC_RESOURCE_TYPE_SEPARATE_SAMPLERS:
                return VK_DESCRIPTOR_TYPE_SAMPLER;
            case SPVC_RESOURCE_TYPE_ACCELERATION_STRUCTURE:
                return VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
            default:
                return -1;
        }
    }
}
