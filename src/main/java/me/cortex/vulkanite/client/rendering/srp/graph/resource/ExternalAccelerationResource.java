package me.cortex.vulkanite.client.rendering.srp.graph.resource;

import me.cortex.vulkanite.client.rendering.srp.api.execution.ExternalResourceTracker;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;

public class ExternalAccelerationResource extends Resource<ExternalAccelerationResource> implements ExternalResource<ExternalAccelerationResource, VAccelerationStructure> {
    private VAccelerationStructure object;

    @Override
    public ExternalAccelerationResource setConcrete(VAccelerationStructure concrete) {
        this.object = concrete;
        ExternalResourceTracker.update(this);
        return this;
    }

    @Override
    public VAccelerationStructure getConcrete() {
        return this.object;
    }
}
