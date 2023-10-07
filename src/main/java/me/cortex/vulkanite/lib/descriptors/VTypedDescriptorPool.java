package me.cortex.vulkanite.lib.descriptors;

import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import me.cortex.vulkanite.lib.base.VContext;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;

import java.nio.LongBuffer;
import java.util.ArrayList;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class VTypedDescriptorPool extends TrackedResourceObject {
    private final VContext ctx;
    private final ArrayList<Long> pools = new ArrayList<>();
    private final ArrayList<Integer> poolFreeSizes = new ArrayList<>();
    private final VDescriptorSetLayout layout;
    private final int flags;

    private static final int nSetsPerPool = 16;

    public VTypedDescriptorPool(VContext ctx, VDescriptorSetLayout layout, int flags) {
        this.ctx = ctx;
        this.layout = layout;
        this.flags = flags;
    }

    private void createNewPool() {
        try (var stack = stackPush()) {
            var sizes = VkDescriptorPoolSize.calloc(layout.types.length, stack);
            for (int i = 0; i < layout.types.length; i++) {
                sizes.get(i).type(layout.types[i]).descriptorCount(nSetsPerPool);
            }
            LongBuffer pPool = stack.mallocLong(1);
            _CHECK_(vkCreateDescriptorPool(ctx.device, VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(flags | VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .maxSets(nSetsPerPool)
                    .pPoolSizes(sizes), null, pPool));
            pools.add(pPool.get(0));
            poolFreeSizes.add(nSetsPerPool);
        }
    }

    public VDescriptorSet allocateSet() {
        if (poolFreeSizes.isEmpty() || poolFreeSizes.get(pools.size() - 1) == 0) {
            createNewPool();
        }
        long pool = pools.get(pools.size() - 1);
        long set;
        poolFreeSizes.set(pools.size() - 1, poolFreeSizes.get(pools.size() - 1) - 1);
        try (var stack = stackPush()) {
            var pSet = stack.mallocLong(1);
            _CHECK_(vkAllocateDescriptorSets(ctx.device, VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(pool)
                    .pSetLayouts(stack.mallocLong(1).put(0, layout.layout)), pSet));
            set = pSet.get(0);
        }
        return new VDescriptorSet(this, pool, set);
    }

    public void freeSet(VDescriptorSet set) {
        int index = pools.indexOf(set.poolHandle);
        try (var stack = stackPush()) {
            var pDescriptorSets = stack.mallocLong(1).put(0, set.set);
            _CHECK_(vkFreeDescriptorSets(ctx.device, set.poolHandle, pDescriptorSets));
        }
        poolFreeSizes.set(index, poolFreeSizes.get(index) + 1);
        if (poolFreeSizes.get(index) == nSetsPerPool) {
            vkDestroyDescriptorPool(ctx.device, set.poolHandle, null);
            pools.remove(index);
            poolFreeSizes.remove(index);
        }
    }

    @Override
    public void free() {
        free0();
        for (long pool : pools) {
            vkDestroyDescriptorPool(ctx.device, pool, null);
        }
    }
}
