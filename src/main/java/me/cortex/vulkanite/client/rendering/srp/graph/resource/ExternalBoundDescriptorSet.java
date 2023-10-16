package me.cortex.vulkanite.client.rendering.srp.graph.resource;

import me.cortex.vulkanite.client.rendering.srp.api.execution.ExternalResourceTracker;
import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;

//This is special as its used to
public class ExternalBoundDescriptorSet extends Resource<ExternalBoundDescriptorSet> implements ExternalResource<ExternalBoundDescriptorSet, Long> {
    private final Layout layout;

    public ExternalBoundDescriptorSet(Layout layout) {
        this.layout = layout;
    }

    public Layout layout() {
        return this.layout;
    }

    private Long object = (long) -1;
    @Override
    public ExternalBoundDescriptorSet setConcrete(Long concrete) {
        this.object = concrete;
        ExternalResourceTracker.update(this);
        return this;
    }

    @Override
    public Long getConcrete() {
        return this.object;
    }

    public Layout getLayout() {
        return this.layout;
    }
}
