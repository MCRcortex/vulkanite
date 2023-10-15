package me.cortex.vulkanite.client.rendering.srp.graph.resource;

public interface ExternalResource <T extends ExternalResource<T, J>, J> {
    T setConcrete(J concrete);
    J getConcrete();
}
