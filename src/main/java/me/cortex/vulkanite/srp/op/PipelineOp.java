package me.cortex.vulkanite.srp.op;

import me.cortex.vulkanite.srp.resource.ResourceAccess;

import java.util.List;

public abstract class PipelineOp {
    public abstract List<ResourceAccess> getResources();
    public abstract int getPipelineStage();
}
