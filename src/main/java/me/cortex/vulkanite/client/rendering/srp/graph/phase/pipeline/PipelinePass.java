package me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.Pipeline;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.Pass;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalBoundLayout;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.Resource;

public abstract class PipelinePass<T extends PipelinePass<T, J>, J extends Pipeline<J>> extends Pass<T> {
    protected final Pipeline<J> pipeline;
    protected final Map<Layout, Object> layoutBindings = new HashMap<>();

    public PipelinePass(Pipeline<J> pipeline) {
        this.pipeline = pipeline;
    }

    public T bindLayout(Layout layout, Resource<?>... bindings) {
        return (T) this.bindLayout(layout, Arrays.stream(bindings).map(List::of).toArray(List[]::new));
    }

    public T bindLayout(Layout layout, List<Resource<?>>... bindings) {
        if (this.layoutBindings.containsKey(layout)) {
            throw new IllegalStateException("Already bound layout");
        }
        var bindingPoints = layout.getBindings();
        if (bindingPoints.size() != bindings.length) {
            throw new IllegalStateException("Incorrect number of binding points");
        }

        for (int i = 0; i < bindingPoints.size(); i++) {
            var bindingPoint = bindingPoints.get(i);
            var bindingList = bindings[i];

            if (bindingPoint.reads()) {
                for (var binding : bindingList) {
                    this.reads(binding);
                }
            }

            if (bindingPoint.writes()) {
                for (var binding : bindingList) {
                    this.writes(binding);
                }
            }
        }

        this.layoutBindings.put(layout, bindings);
        return (T)this;
    }

    public T bindLayout(Layout layout, ExternalBoundLayout binding) {
        if (this.layoutBindings.containsKey(layout)) {
            throw new IllegalStateException("Already bound layout");
        }
        this.layoutBindings.put(layout, binding);
        return (T)this;
    }

    public T bindLayout(int index, Resource<?>... bindings) {
        return this.bindLayout(this.pipeline.getLayout(index), bindings);
    }

    public T bindLayout(int index, ExternalBoundLayout binding) {
        return this.bindLayout(this.pipeline.getLayout(index), binding);
    }

    public T bindLayout(int index, List<Resource<?>>... bindings) {
        return this.bindLayout(this.pipeline.getLayout(index), bindings);
    }

    public T bindLayout(Resource<?>... bindings) {
        return this.bindLayout(0, bindings);
    }
}
