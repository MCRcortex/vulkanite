package me.cortex.vulkanite.client.rendering.graph.phase;

import me.cortex.vulkanite.client.rendering.graph.resource.Resource;

public class TracePass extends Pass<TracePass> {
    public TracePass reads(Resource<?> resource) {
        return super.reads(resource);
    }
    public TracePass writes(Resource<?> resource) {
        return super.writes(resource);
    }
}

