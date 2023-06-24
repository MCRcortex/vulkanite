package me.cortex.vulkanite.lib.cmd;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

//Manages multiple command queues and fence synchronizations
public class CommandManager {
    private final VkDevice device;
    private final VkQueue[] queues;
    private final VCommandPool transientPool;

    public CommandManager(VkDevice device, int queues) {
        this.device = device;
        this.queues = new VkQueue[queues];
        try (var stack = stackPush()) {
            var pQ = stack.pointers(0);
            for (int i = 0; i < queues; i++) {
                vkGetDeviceQueue(device, 0, i, pQ);
                this.queues[i] = new VkQueue(pQ.get(0), device);
            }
        }

        transientPool = new VCommandPool(device, VK_COMMAND_POOL_CREATE_TRANSIENT_BIT);
    }

    public void submit(int queueId, VkSubmitInfo submit) {
        try (var stack = stackPush()) {
            vkQueueSubmit(queues[queueId], submit, 0);
        }
    }

    public synchronized VCmdBuff singleTimeCommand() {
        return transientPool.createCommandBuffers(1)[0];
    }
}
