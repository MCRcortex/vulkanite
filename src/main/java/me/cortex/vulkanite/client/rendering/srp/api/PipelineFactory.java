package me.cortex.vulkanite.client.rendering.srp.api;

import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.ComputePipeline;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.TracePipeline;

public class PipelineFactory {
    public ComputePipeline createComputePipeline(Layout... layouts) {
        return new ComputePipeline(layouts);
    }

    public TracePipeline createTracePipeline(Layout... layouts) {
        return new TracePipeline(layouts);
    }
}
