package me.cortex.vulkanite.client.rendering.srp.graph.resource;

import me.cortex.vulkanite.client.rendering.srp.api.execution.ExternalResourceTracker;
import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSet;

//This is special as its used to
public class ExternalBoundLayout extends Resource<ExternalBoundLayout> implements ExternalResource<ExternalBoundLayout, Long> {
    private final Layout layout;

    public ExternalBoundLayout(Layout layout) {
        this.layout = layout;
    }

    public Layout layout() {
        return this.layout;
    }

    private Long object = (long) -1;
    @Override
    public ExternalBoundLayout setConcrete(Long concrete) {
        this.object = concrete;
        ExternalResourceTracker.update(this);
        return this;
    }

    @Override
    public Long getConcrete() {
        return this.object;
    }
}
