package me.cortex.vulkanite.client.rendering.graph.resource;

public abstract class Resource <T extends Resource<T>> {
    private String name;

    public T name(String name) {
        this.name = name;
        return (T) this;
    }
}
