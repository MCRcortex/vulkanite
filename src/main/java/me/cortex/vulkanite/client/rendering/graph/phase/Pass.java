package me.cortex.vulkanite.client.rendering.graph.phase;

import me.cortex.vulkanite.client.rendering.graph.resource.Resource;

import java.util.List;

public abstract class Pass<T extends Pass<T>> {
    private String name;

    public T name(String name) {
        this.name = name;
        return (T) this;
    }

    public abstract List<Resource<?>> reads();
    public abstract List<Resource<?>> writes();
}
