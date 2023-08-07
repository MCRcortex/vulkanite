package me.cortex.vulkanite.lib.pipeline;

import me.cortex.vulkanite.lib.base.VContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;
import java.util.LinkedList;
import java.util.List;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;

public class DescriptorSetLayoutBuilder {
    private VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(0);
    public DescriptorSetLayoutBuilder binding(int binding, int type, int count, int stages) {
        bindings = VkDescriptorSetLayoutBinding.create(MemoryUtil.nmemRealloc(bindings.address(), (bindings.capacity() + 1L) * VkDescriptorSetLayoutBinding.SIZEOF), bindings.capacity() + 1);
        var struct = bindings.get(bindings.capacity()-1);
        struct.set(binding, type, count, stages, null);
        return this;
    }

    public DescriptorSetLayoutBuilder binding(int binding, int type, int stages) {
        return binding(binding, type, 1, stages);
    }
    public DescriptorSetLayoutBuilder binding(int type, int stages) {
        return binding(bindings.capacity(), type, stages);
    }

    int flags;
    public DescriptorSetLayoutBuilder() {
        this(0);
    }
    public DescriptorSetLayoutBuilder(int flags){
        this.flags = flags;
    }

    public VDescriptorSetLayout build(VContext ctx) {
        try (var stack = stackPush()) {
            var info = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pBindings(bindings)
                    .flags(flags);

            LongBuffer pBuffer = stack.mallocLong(1);
            _CHECK_(vkCreateDescriptorSetLayout(ctx.device, info, null, pBuffer));
            return new VDescriptorSetLayout(ctx, pBuffer.get(0));
        }
    }
}
