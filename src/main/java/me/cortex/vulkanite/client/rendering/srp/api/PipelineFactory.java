package me.cortex.vulkanite.client.rendering.srp.api;

import me.cortex.vulkanite.client.rendering.srp.api.pipeline.ComputePipeline;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.TracePipeline;

public class PipelineFactory {
    public ComputePipeline createComputePipeline() {
        return new ComputePipeline();
    }

    public TracePipeline createTracePipeline() {
        return new TracePipeline();
    }
}
