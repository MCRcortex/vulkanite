package me.cortex.vulkanite.client.rendering.srp.graph.resource;

import me.cortex.vulkanite.client.rendering.srp.api.execution.ExternalResourceTracker;
import me.cortex.vulkanite.lib.memory.VImage;

public class ExternalImageResource extends ImageResource implements ExternalResource<ExternalImageResource, VImage> {
    private VImage object;

    @Override
    public ExternalImageResource setConcrete(VImage concrete) {
        this.object = concrete;
        ExternalResourceTracker.update(this);
        return this;
    }

    @Override
    public VImage getConcrete() {
        return this.object;
    }
}
