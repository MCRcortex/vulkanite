package me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline;

import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.Pipeline;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.Pass;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalBoundLayout;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.Resource;

public abstract class PipelinePass<T extends PipelinePass<T, J>, J extends Pipeline<J>> extends Pass<T> {
    protected final Pipeline<J> pipeline;

    public PipelinePass(Pipeline<J> pipeline) {
        this.pipeline = pipeline;
    }

    public T bindLayout(Layout layout, Resource<?>... bindings) {
        var bindingPoints = layout.getBindings();

        return (T)this;
    }

    public T bindLayout(Layout layout, ExternalBoundLayout binding) {
        return (T)this;
    }

    public T bindLayout(int index, Resource<?>... bindings) {
        return this.bindLayout(this.pipeline.getLayout(index), bindings);
    }

    public T bindLayout(int index, ExternalBoundLayout binding) {
        return this.bindLayout(this.pipeline.getLayout(index), binding);
    }

    public T bindLayout(Resource<?>... bindings) {
        return this.bindLayout(0, bindings);
    }
}
