package me.cortex.vulkanite.lib.pipeline;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.VObject;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;

public class VComputePipeline extends VObject {
    private final VContext context;
    private final long pipeline;
    private final long layout;

    public VComputePipeline(VContext context, long layout, long pipeline) {
        this.context = context;
        this.layout = layout;
        this.pipeline = pipeline;
    }

    public long layout() {
        return layout;
    }

    public long pipeline() {
        return pipeline;
    }

    @Override
    public void free() {
        vkDestroyPipeline(context.device, pipeline, null);
    }
}
