package me.cortex.vulkanite.client.rendering.srp.graph.resource;

import me.cortex.vulkanite.client.rendering.srp.api.execution.ExternalResourceTracker;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;

public class ExternalBufferResource extends BufferResource implements ExternalResource<ExternalBufferResource, VBuffer> {
    private VBuffer object;

    @Override
    public ExternalBufferResource setConcrete(VBuffer concrete) {
        this.object = concrete;
        ExternalResourceTracker.update(this);
        return this;
    }

    @Override
    public VBuffer getConcrete() {
        return this.object;
    }
}
