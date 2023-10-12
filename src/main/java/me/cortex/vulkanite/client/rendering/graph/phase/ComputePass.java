package me.cortex.vulkanite.client.rendering.graph.phase;

import me.cortex.vulkanite.client.rendering.graph.resource.Resource;

public class ComputePass extends Pass<ComputePass> {
    public ComputePass reads(Resource<?> resource) {
        return super.reads(resource);
    }

    public ComputePass writes(Resource<?> resource) {
        return super.writes(resource);
    }
}
