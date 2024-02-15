package me.cortex.vulkanite.lib.other;

import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkQueryPoolCreateInfo;

import java.nio.LongBuffer;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class VQueryPool extends VObject {
    public final long pool;
    private final VkDevice device;
    private VQueryPool(VkDevice device, int count, int type) {
        this.device = device;
        try (MemoryStack stack = stackPush()) {
            LongBuffer pQueryPool = stack.mallocLong(1);
            _CHECK_(vkCreateQueryPool(device,
                    VkQueryPoolCreateInfo
                            .calloc(stack)
                            .sType$Default()
                            .queryCount(count)
                            .queryType(type),
                    null, pQueryPool), "Failed to create query pool");
            pool = pQueryPool.get(0);
        }
    }

    public static VRef<VQueryPool> create(VkDevice device, int count, int type) {
        return new VRef<>(new VQueryPool(device, count, type));
    }

    public long[] getResultsLong(int count) {
        return getResultsLong(0, count, VK_QUERY_RESULT_WAIT_BIT);
    }

    public long[] getResultsLong(int start, int count, int flags) {
        try (var stack = stackPush()) {
            LongBuffer results = stack.mallocLong(count);
            _CHECK_(vkGetQueryPoolResults(device, pool, start, count, results, Long.BYTES, VK_QUERY_RESULT_64_BIT | flags));
            var res = new long[count];
            results.get(res);
            return res;
        }
    }

    protected void free() {
        vkDestroyQueryPool(device, pool, null);
    }
}
