package me.cortex.vulkanite.client.rendering.srp;

import me.cortex.vulkanite.client.rendering.srp.api.pipeline.ComputePipeline;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.TracePipeline;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.AccelerationResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalBoundLayout;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalImageResource;

public class SRPContext {
    public ExternalBoundLayout getExternalLayout(String identifier) {
        return new ExternalBoundLayout().name(identifier);
    }

    public AccelerationResource getAccelerationStructure(String identifier) {
        return new AccelerationResource().name(identifier);
    }

    public ExternalImageResource getExternalTexture(String identifier) {
        return (ExternalImageResource) new ExternalImageResource().name(identifier);
    }
}
