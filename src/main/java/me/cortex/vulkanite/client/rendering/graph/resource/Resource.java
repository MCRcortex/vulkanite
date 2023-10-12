package me.cortex.vulkanite.client.rendering.graph.resource;

import me.cortex.vulkanite.client.rendering.graph.phase.Pass;

import java.util.ArrayList;
import java.util.List;

public abstract class Resource <T extends Resource<T>> {
    private String name;

    public T name(String name) {
        this.name = name;
        return (T) this;
    }

    public T reads(Pass<?> pass) {
        System.out.println("Pass " + pass.name() + " reads " + this.name);
        return (T) this;
    }

    public T writes(Pass<?> pass) {
        System.out.println("Pass " + pass.name() + " writes " + this.name);
        return (T) this;
    }

    private record Op(boolean isRead, Pass<?> pass) {}
    private final List<List<Op>> versions = new ArrayList<>();
    //A version is a complete information flow, e.g. where the first value is a write and the last is a read
    // each version is independent of eachother however dependencies within a version are order specific and depend on each
    // previous pass

}
