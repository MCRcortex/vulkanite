package me.cortex.vulkanite.client.rendering.srp.api.execution;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.other.sync.VFence;

public class ExecutionContext {
    public final VContext ctx;
    public final VCmdBuff cmd;
    public final VFence fence;

    public ExecutionContext(VContext ctx, VCmdBuff cmd, VFence fence) {
        this.ctx = ctx;
        this.cmd = cmd;
        this.fence = fence;
    }
}
