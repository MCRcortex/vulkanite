package me.cortex.vulkanite.client.rendering.srp.api.pipeline;

import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import java.util.List;

public abstract class Pipeline <T extends Pipeline<T, J>, J> {
    protected final J concretePipeline;

    protected final List<Layout> layouts;

    protected Pipeline(J concretePipeline, List<Layout> layouts) {
        this.concretePipeline = concretePipeline;
        this.layouts = layouts;
    }

    public Layout getLayoutSet(int index) {
        return this.layouts.get(index);
    }

    public J getConcretePipeline() {
        return this.concretePipeline;
    }

    public List<Layout> getLayouts() {
        return this.layouts;
    }
}
