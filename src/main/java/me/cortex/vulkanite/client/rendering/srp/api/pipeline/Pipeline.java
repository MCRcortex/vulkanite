package me.cortex.vulkanite.client.rendering.srp.api.pipeline;

import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;

import java.util.List;

public abstract class Pipeline <T extends Pipeline<T>> {
    protected final List<Layout> layouts;

    protected Pipeline(List<Layout> layouts) {
        this.layouts = layouts;
    }

    public Layout getLayout(int index) {
        return layouts.get(0);
    }
}
