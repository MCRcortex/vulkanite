package me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline;

import me.cortex.vulkanite.client.rendering.srp.api.pipeline.ComputePipeline;

public class ComputePass extends PipelinePass<ComputePass, ComputePipeline> {
    public ComputePass(ComputePipeline pipeline) {
        super(pipeline);
    }
}
