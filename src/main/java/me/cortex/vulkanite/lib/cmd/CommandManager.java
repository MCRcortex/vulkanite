package me.cortex.vulkanite.lib.cmd;

import me.cortex.vulkanite.lib.other.sync.VFence;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.LongBuffer;

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

    public void submit(int queueId, VCmdBuff[] cmdBuffs, VSemaphore[] waits, int[] waitStages, VSemaphore[] triggers, VFence fence) {
        try (var stack = stackPush()) {
            LongBuffer waitSemaphores = stack.mallocLong(waits.length);
            LongBuffer signalSemaphores = stack.mallocLong(triggers.length);
            for (var wait : waits) {waitSemaphores.put(wait.address());}
            for (var trigger : triggers) {signalSemaphores.put(trigger.address());}
            waitSemaphores.rewind();
            signalSemaphores.rewind();
            var submit = VkSubmitInfo.calloc(stack).sType$Default()
                    .pCommandBuffers(stack.pointers(cmdBuffs))
                    .pWaitSemaphores(waitSemaphores)
                    .waitSemaphoreCount(waits.length)
                    .pWaitDstStageMask(stack.ints(waitStages))
                    .pSignalSemaphores(signalSemaphores);
            vkQueueSubmit(queues[queueId], submit, fence==null?0:fence.address());
        }
    }

    public synchronized VCmdBuff singleTimeCommand() {
        return transientPool.createCommandBuffers(1)[0];
    }
}
