package me.cortex.vulkanite.lib.descriptors;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.VImageView;
import me.cortex.vulkanite.lib.other.VSampler;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.VkWriteDescriptorSetAccelerationStructureKHR;

import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class DescriptorUpdateBuilder {
    private final VContext ctx;
    private final MemoryStack stack;
    private final VkWriteDescriptorSet.Buffer updates;
    public DescriptorUpdateBuilder(VContext ctx, int maxUpdates) {
        this.ctx = ctx;
        this.stack = MemoryStack.stackPush();
        this.updates = VkWriteDescriptorSet.calloc(maxUpdates, stack);
    }

    private long set;
    public DescriptorUpdateBuilder set(long set) {
        this.set = set;
        return this;
    }

    public DescriptorUpdateBuilder buffer(int binding, VBuffer buffer) {
        return buffer(binding, buffer, 0, VK_WHOLE_SIZE);
    }
    public DescriptorUpdateBuilder buffer(int binding, VBuffer buffer, long offset, long range) {
        updates.get()
                .sType$Default()
                .dstBinding(binding)
                .dstSet(set)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1)
                .pBufferInfo(VkDescriptorBufferInfo
                        .calloc(1, stack)
                        .buffer(buffer.buffer())
                        .offset(offset)
                        .range(range));

        return this;
    }


    public DescriptorUpdateBuilder uniform(int binding, VBuffer buffer) {
        return uniform(binding, buffer, 0, VK_WHOLE_SIZE);
    }
    public DescriptorUpdateBuilder uniform(int binding, VBuffer buffer, long offset, long range) {
        updates.get()
                .sType$Default()
                .dstBinding(binding)
                .dstSet(set)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(1)
                .pBufferInfo(VkDescriptorBufferInfo
                        .calloc(1, stack)
                        .buffer(buffer.buffer())
                        .offset(offset)
                        .range(range));
        return this;
    }

    public DescriptorUpdateBuilder acceleration(int binding, VAccelerationStructure... structures) {
        var buff = stack.mallocLong(structures.length);
        for (var structure : structures) {
            buff.put(structure.structure);
        }
        buff.rewind();
        updates.get()
                .sType$Default()
                .dstBinding(binding)
                .dstSet(set)
                .descriptorType(VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR)
                .descriptorCount(structures.length)
                .pNext(VkWriteDescriptorSetAccelerationStructureKHR.calloc(stack)
                        .sType$Default()
                        .pAccelerationStructures(buff));
        return this;
    }

    public DescriptorUpdateBuilder imageStore(int binding, VImageView view) {
        return imageStore(binding, VK_IMAGE_LAYOUT_GENERAL, view);
    }
    public DescriptorUpdateBuilder imageStore(int binding, int layout, VImageView view) {
        updates.get()
                .sType$Default()
                .dstBinding(binding)
                .dstSet(set)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .descriptorCount(1)
                .pImageInfo(VkDescriptorImageInfo
                        .calloc(1, stack)
                        .imageLayout(layout)
                        .imageView(view.view));
        return this;
    }

    public DescriptorUpdateBuilder imageSampler(int binding, VImageView view, VSampler sampler) {
        return imageSampler(binding, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, view, sampler);
    }

    public DescriptorUpdateBuilder imageSampler(int binding, int layout, VImageView view, VSampler sampler) {
        updates.get()
                .sType$Default()
                .dstBinding(binding)
                .dstSet(set)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(VkDescriptorImageInfo
                        .calloc(1, stack)
                        .imageLayout(layout)
                        .imageView(view.view)
                        .sampler(sampler.sampler));
        return this;
    }

    public void apply() {
        updates.limit(updates.position());
        updates.rewind();
        vkUpdateDescriptorSets(ctx.device, updates, null);
        stack.pop();
    }
}
