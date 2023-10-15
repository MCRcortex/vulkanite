package me.cortex.vulkanite.client.rendering.srp.graph;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.srp.api.execution.ExecutionContext;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.Pass;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.other.sync.VFence;

public class GraphExecutor {
    public static void execute(RenderGraph graph, VCmdBuff cmdBuffer, VFence fence) {
        var ectx = new ExecutionContext(Vulkanite.INSTANCE.getCtx(), cmdBuffer, fence);
        for (Pass<?> pass : graph.ordering) {
            pass.execute(ectx);
        }
    }
}
