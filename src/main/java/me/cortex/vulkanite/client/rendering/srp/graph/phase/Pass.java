package me.cortex.vulkanite.client.rendering.srp.graph.phase;

import me.cortex.vulkanite.client.rendering.srp.api.execution.ExecutionContext;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.Resource;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class Pass<T extends Pass<T>> {
    private String name;
    private final Set<Resource<?>> uses = new HashSet<>();

    public T name(String name) {
        this.name = name;
        return (T) this;
    }

    protected T reads(Resource<?> resource) {
        resource.reads(this);
        this.uses.add(resource);
        return (T) this;
    }

    protected T writes(Resource<?> resource) {
        resource.writes(this);
        this.uses.add(resource);
        return (T) this;
    }

    public String name() {
        return this.name;
    }

    public Collection<Resource<?>> uses() {
        return uses;
    }

    //Verifies that everything is bound correctly and validly bound
    public void verify(){};

    public void execute(ExecutionContext ctx) {
        throw new IllegalStateException();
    }
}
