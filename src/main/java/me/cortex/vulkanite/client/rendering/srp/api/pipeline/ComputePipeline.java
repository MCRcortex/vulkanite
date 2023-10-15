package me.cortex.vulkanite.client.rendering.srp.api.pipeline;

import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.lib.pipeline.VComputePipeline;

import java.util.ArrayList;
import java.util.List;

public class ComputePipeline extends Pipeline<ComputePipeline, VComputePipeline> {
    public ComputePipeline(VComputePipeline pipeline, Layout... layouts) {
        this(pipeline, List.of(layouts));
    }

    public ComputePipeline(VComputePipeline pipeline, List<Layout> layouts) {
        super(pipeline, layouts);
    }
}
