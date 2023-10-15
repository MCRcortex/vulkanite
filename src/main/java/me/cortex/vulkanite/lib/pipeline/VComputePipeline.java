package me.cortex.vulkanite.lib.pipeline;

import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;

import static org.lwjgl.vulkan.VK10.*;

public class VComputePipeline extends TrackedResourceObject {
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
        free0();
        vkDestroyPipeline(context.device, pipeline, null);
        vkDestroyPipelineLayout(context.device, layout, null);
    }

    public void bind(VCmdBuff cmd) {
        vkCmdBindPipeline(cmd.buffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
    }

    public void dispatch(VCmdBuff cmd, int x, int y, int z) {
        vkCmdDispatch(cmd.buffer, x, y, z);
    }

    public void bindDSets(VCmdBuff cmd, long... descs) {
        vkCmdBindDescriptorSets(cmd.buffer, VK_PIPELINE_BIND_POINT_COMPUTE, layout, 0, descs, null);
    }
}
