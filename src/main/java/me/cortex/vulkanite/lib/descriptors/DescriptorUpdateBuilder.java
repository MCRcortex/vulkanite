package me.cortex.vulkanite.lib.descriptors;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.VImageView;
import me.cortex.vulkanite.lib.other.VSampler;
import me.cortex.vulkanite.lib.shader.reflection.ShaderReflection;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.VkWriteDescriptorSetAccelerationStructureKHR;

import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.VK10.*;

import java.util.ArrayList;
import java.util.List;

public class DescriptorUpdateBuilder {
    private final VContext ctx;
    private final MemoryStack stack;
    private final VkWriteDescriptorSet.Buffer updates;
    private final VImageView placeholderImageView;
    private ArrayList<VkDescriptorBufferInfo.Buffer> bulkBufferInfos = new ArrayList<>();
    private ArrayList<VkDescriptorImageInfo.Buffer> bulkImageInfos = new ArrayList<>();
    private ShaderReflection.Set refSet = null;

    public DescriptorUpdateBuilder(VContext ctx, int maxUpdates) {
        this(ctx, maxUpdates, null);
    }

    public DescriptorUpdateBuilder(VContext ctx, int maxUpdates, VImageView placeholderImageView) {
        this.ctx = ctx;
        // this.stack = MemoryStack.stackPush();
        int objSize = Integer.max(
                Integer.max(
                        VkDescriptorBufferInfo.SIZEOF,
                        VkDescriptorImageInfo.SIZEOF),
                VkWriteDescriptorSetAccelerationStructureKHR.SIZEOF);
        objSize = ((objSize + 15) / 16) * 16;
        this.stack = MemoryStack.create(1024 + maxUpdates * VkWriteDescriptorSet.SIZEOF + maxUpdates * objSize);
        this.stack.push();
        this.updates = VkWriteDescriptorSet.calloc(maxUpdates, stack);
        this.placeholderImageView = placeholderImageView;
    }

    public DescriptorUpdateBuilder(VContext ctx, ShaderReflection.Set refSet) {
        this(ctx, refSet, null);
    }

    public DescriptorUpdateBuilder(VContext ctx, ShaderReflection.Set refSet, VImageView placeholderImageView) {
        this(ctx, refSet.bindings().size(), placeholderImageView);
        this.refSet = refSet;
    }

    private long viewOrPlaceholder(VImageView v) {
        if (v == null && placeholderImageView == null) return 0;
        return v == null ? placeholderImageView.view : v.view;
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
        if (refSet != null && refSet.getBindingAt(binding) == null) {
            return this;
        }
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

    public DescriptorUpdateBuilder buffer(int binding, int dstArrayElement, List<VBuffer> buffers) {
        if (refSet != null && refSet.getBindingAt(binding) == null) {
            return this;
        }
        var bufInfo = VkDescriptorBufferInfo.calloc(buffers.size());
        for (int i = 0; i < buffers.size(); i++) {
            bufInfo.get(i)
                    .buffer(buffers.get(i).buffer())
                    .offset(0)
                    .range(VK_WHOLE_SIZE);
        }
        updates.get()
                .sType$Default()
                .dstBinding(binding)
                .dstSet(set)
                .dstArrayElement(dstArrayElement)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(buffers.size())
                .pBufferInfo(bufInfo);
        bulkBufferInfos.add(bufInfo);
        return this;
    }


    public DescriptorUpdateBuilder uniform(int binding, VBuffer buffer) {
        return uniform(binding, buffer, 0, VK_WHOLE_SIZE);
    }
    public DescriptorUpdateBuilder uniform(int binding, VBuffer buffer, long offset, long range) {
        if (refSet != null && refSet.getBindingAt(binding) == null) {
            return this;
        }
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
        if (refSet != null && refSet.getBindingAt(binding) == null) {
            return this;
        }
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

    public DescriptorUpdateBuilder imageStore(int binding, int dstArrayElement, List<VImageView> views) {
        if (refSet != null && refSet.getBindingAt(binding) == null) {
            return this;
        }
        var imgInfo = VkDescriptorImageInfo.calloc(views.size());
        for (int i = 0; i < views.size(); i++) {
            imgInfo.get(i)
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .imageView(viewOrPlaceholder(views.get(i)));
        }
        updates.get()
                .sType$Default()
                .dstBinding(binding)
                .dstSet(set)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .descriptorCount(views.size())
                .pImageInfo(imgInfo);
        bulkImageInfos.add(imgInfo);
        return this;
    }
    public DescriptorUpdateBuilder imageStore(int binding, VImageView view) {
        return imageStore(binding, VK_IMAGE_LAYOUT_GENERAL, view);
    }
    public DescriptorUpdateBuilder imageStore(int binding, int layout, VImageView view) {
        if (refSet != null && refSet.getBindingAt(binding) == null) {
            return this;
        }
        updates.get()
                .sType$Default()
                .dstBinding(binding)
                .dstSet(set)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .descriptorCount(1)
                .pImageInfo(VkDescriptorImageInfo
                        .calloc(1, stack)
                        .imageLayout(layout)
                        .imageView(viewOrPlaceholder(view)));
        return this;
    }

    public DescriptorUpdateBuilder imageSampler(int binding, VImageView view, VSampler sampler) {
        return imageSampler(binding, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, view, sampler);
    }

    public DescriptorUpdateBuilder imageSampler(int binding, int layout, VImageView view, VSampler sampler) {
        if (refSet != null && refSet.getBindingAt(binding) == null) {
            return this;
        }
        updates.get()
                .sType$Default()
                .dstBinding(binding)
                .dstSet(set)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(VkDescriptorImageInfo
                        .calloc(1, stack)
                        .imageLayout(layout)
                        .imageView(viewOrPlaceholder(view))
                        .sampler(sampler.sampler));
        return this;
    }

    public void apply() {
        updates.limit(updates.position());
        updates.rewind();
        vkUpdateDescriptorSets(ctx.device, updates, null);
        stack.pop();
        for (var bufInfo : bulkBufferInfos) {
            bufInfo.free();
        }
        bulkBufferInfos.clear();
        for (var imgInfo : bulkImageInfos) {
            imgInfo.free();
        }
        bulkImageInfos.clear();
    }
}
