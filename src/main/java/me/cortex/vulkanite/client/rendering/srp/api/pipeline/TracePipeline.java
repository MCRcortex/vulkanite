package me.cortex.vulkanite.client.rendering.srp.api.pipeline;

import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.lib.pipeline.VComputePipeline;
import me.cortex.vulkanite.lib.pipeline.VRaytracePipeline;

import java.util.ArrayList;
import java.util.List;

public class TracePipeline extends Pipeline<TracePipeline, VRaytracePipeline> {
    public TracePipeline(VRaytracePipeline pipeline, Layout... layouts) {
        this(pipeline, List.of(layouts));
    }

    public TracePipeline(VRaytracePipeline pipeline, List<Layout> layouts) {
        super(pipeline, layouts);
    }
}
