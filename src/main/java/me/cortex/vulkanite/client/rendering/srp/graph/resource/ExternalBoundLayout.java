package me.cortex.vulkanite.client.rendering.srp.graph.resource;

import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;

//This is special as its used to
public class ExternalBoundLayout extends Resource<ExternalBoundLayout> implements ExternalResource<ExternalBoundLayout> {
    private final Layout layout;

    public ExternalBoundLayout(Layout layout) {
        this.layout = layout;
    }

    public Layout layout() {
        return this.layout;
    }
}
