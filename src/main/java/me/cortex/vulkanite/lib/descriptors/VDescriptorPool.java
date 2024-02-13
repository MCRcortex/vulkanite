package me.cortex.vulkanite.lib.descriptors;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VRef;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetVariableDescriptorCountAllocateInfo;

import java.nio.LongBuffer;
import java.util.ArrayList;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class VDescriptorPool extends VObject {
    private final VContext ctx;
    private final ArrayList<Long> pools = new ArrayList<>();
    private final ArrayList<Integer> poolFreeSizes = new ArrayList<>();
    private final VRef<VDescriptorSetLayout> layout;
    private final int flags;

    private static final int nSetsPerPool = 16;
    private final int countPerType;

    private VDescriptorPool(VContext ctx, VRef<VDescriptorSetLayout> layout, int flags, int countPerType) {
        this.ctx = ctx;
        this.layout = layout.addRef();
        this.flags = flags;
        this.countPerType = countPerType;
    }

    public static VRef<VDescriptorPool> create(VContext ctx, VRef<VDescriptorSetLayout> layout, int flags) {
        return new VRef<>(new VDescriptorPool(ctx, layout, flags, 1));
    }

    public static VRef<VDescriptorPool> create(VContext ctx, VRef<VDescriptorSetLayout> layout, int flags, int countPerType) {
        return new VRef<>(new VDescriptorPool(ctx, layout, flags, countPerType));
    }

    private void createNewPool() {
        try (var stack = stackPush()) {
            var sizes = VkDescriptorPoolSize.calloc(layout.get().types.length, stack);
            for (int i = 0; i < layout.get().types.length; i++) {
                sizes.get(i).type(layout.get().types[i]).descriptorCount(nSetsPerPool * countPerType);
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

    public VRef<VDescriptorSet> allocateSet(int variableSize) {
        if (poolFreeSizes.isEmpty() || poolFreeSizes.get(pools.size() - 1) == 0) {
            createNewPool();
        }
        long pool = pools.get(pools.size() - 1);
        long set;
        poolFreeSizes.set(pools.size() - 1, poolFreeSizes.get(pools.size() - 1) - 1);
        try (var stack = stackPush()) {
            var pSet = stack.mallocLong(1);
            var allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(pool)
                    .pSetLayouts(stack.longs(layout.get().layout));
            if (variableSize >= 0) {
                var variableCountInfo = VkDescriptorSetVariableDescriptorCountAllocateInfo.calloc(stack)
                        .sType$Default()
                        .pDescriptorCounts(stack.ints(variableSize));
                allocInfo.pNext(variableCountInfo.address());
            }
            _CHECK_(vkAllocateDescriptorSets(ctx.device, allocInfo, pSet));
            set = pSet.get(0);
        }
        return new VRef<>(new VDescriptorSet(this, pool, set));
    }

    public VRef<VDescriptorSet> allocateSet() {
        return allocateSet(-1);
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
    protected void free() {
        for (long pool : pools) {
            vkDestroyDescriptorPool(ctx.device, pool, null);
        }
    }
}
