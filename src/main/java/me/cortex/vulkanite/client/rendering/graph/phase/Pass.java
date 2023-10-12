package me.cortex.vulkanite.client.rendering.graph.phase;

import me.cortex.vulkanite.client.rendering.graph.resource.Resource;

import java.util.List;

public abstract class Pass<T extends Pass<T>> {
    private String name;

    public T name(String name) {
        this.name = name;
        return (T) this;
    }

    protected T reads(Resource<?> resource) {
        resource.reads(this);
        return (T) this;
    }

    protected T writes(Resource<?> resource) {
        resource.writes(this);
        return (T) this;
    }

    public String name() {
        return this.name;
    }
}
