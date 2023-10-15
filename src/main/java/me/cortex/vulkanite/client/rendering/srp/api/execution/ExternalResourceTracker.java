package me.cortex.vulkanite.client.rendering.srp.api.execution;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalResource;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ExternalResourceTracker {
    private static final Reference2ObjectMap<ExternalResource<?,?>, Set<Consumer<ExternalResource<?,?>>>> CALLBACKS = new Reference2ObjectOpenHashMap<>();

    public static <T extends ExternalResource<T,J>,J> void registerCallback(ExternalResource<T, J> resource, Consumer<T> callback) {
        CALLBACKS.computeIfAbsent(resource, a->new LinkedHashSet<>()).add((Consumer<ExternalResource<?, ?>>) callback);
    }

    public static <T extends ExternalResource<T,J>,J> void update(ExternalResource<T, J> externalResource) {
        CALLBACKS.getOrDefault(externalResource, Set.of()).forEach(callback->callback.accept(externalResource));
    }

    public static void resetAllCallbacks() {
        CALLBACKS.clear();
    }
}
