package me.cortex.vulkanite.client.rendering.srp.api.pipeline;

import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;

import java.util.ArrayList;
import java.util.List;

public class TracePipeline extends Pipeline<TracePipeline> {
    public TracePipeline(Layout... layouts) {
        this(List.of(layouts));
    }

    public TracePipeline(List<Layout> layouts) {
        super(layouts);
    }
}
