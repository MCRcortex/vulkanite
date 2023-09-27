package me.cortex.vulkanite.client.rendering.graph.phase;

import me.cortex.vulkanite.client.rendering.graph.resource.Resource;

public class TracePass extends Pass<TracePass> {
    public TracePass reads(Resource<?> resource) {
        return this;
    }
    public TracePass writes(Resource<?> resource) {
        return this;
    }
}

