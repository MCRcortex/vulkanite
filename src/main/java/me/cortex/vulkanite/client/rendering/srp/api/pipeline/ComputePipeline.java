package me.cortex.vulkanite.client.rendering.srp.api.pipeline;

import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.lib.pipeline.VComputePipeline;

import java.util.ArrayList;
import java.util.List;

public class ComputePipeline extends Pipeline<ComputePipeline> {
    public ComputePipeline(VComputePipeline pipeline, Layout... layouts) {
        super(List.of(layouts));
    }
}
