package me.cortex.vulkanite.client.rendering.srp.graph.resource;

import me.cortex.vulkanite.client.rendering.srp.graph.phase.Pass;

import java.util.*;

public abstract class Resource <T extends Resource<T>> {
    private String name;

    private final List<Pass<?>> writers = new ArrayList<>();
    private final List<Pass<?>> readers = new ArrayList<>();
    private Map<Pass<?>, Set<Pass<?>>> dependencies = new HashMap<>();

    public T name(String name) {
        this.name = name;
        return (T) this;
    }

    public String name() {
        return name;
    }

    public T reads(Pass<?> pass) {
        var depends = this.dependencies.computeIfAbsent(pass, p -> new LinkedHashSet<>());
        //reads only depend on all previous writes
        depends.addAll(this.writers);
        //Add the pass to the readers
        this.readers.add(pass);
        return (T) this;
    }

    public T writes(Pass<?> pass) {
        var depends = this.dependencies.computeIfAbsent(pass, p -> new LinkedHashSet<>());
        //write depends on all previous writes
        depends.addAll(this.writers);
        //and all previous reads
        depends.addAll(this.readers);
        //Add the pass to the writers
        this.writers.add(pass);
        return (T) this;
    }

    public Map<Pass<?>, Set<Pass<?>>> getDependencyMap() {
        return this.dependencies;
    }

    public Pass<?> getLastWrite() {
        if (this.writers.isEmpty()) {
            return null;
        }
        return this.writers.get(this.writers.size()-1);
    }

}
