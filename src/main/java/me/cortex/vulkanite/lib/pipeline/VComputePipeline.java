package me.cortex.vulkanite.lib.pipeline;

import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.other.sync.VFence;

public class VComputePipeline extends TrackedResourceObject {
    private final VContext context;
    private final long pipeline;
    private final long layout;

    public VComputePipeline(VContext context, long layout, long pipeline) {
        this.context = context;
        this.layout = layout;
        this.pipeline = pipeline;
    }

    @Override
    public void free() {

    }
}
