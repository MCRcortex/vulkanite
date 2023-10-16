package me.cortex.vulkanite.client.rendering.srp.graph;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import me.cortex.vulkanite.client.rendering.srp.api.VirtualResourceMapper;
import me.cortex.vulkanite.client.rendering.srp.api.execution.ExecutionContext;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.Pass;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.Resource;

import java.util.*;

public class RenderGraph implements VirtualResourceMapper {
    private final Reference2ObjectMap<Pass<?>, Set<Pass<?>>> dependents = new Reference2ObjectOpenHashMap<>();
    private final List<Resource<?>> outputs;

    private final Set<Resource<?>> graphResources = new LinkedHashSet<>();

    final List<Pass<?>> ordering = new ArrayList<>();

    public RenderGraph(Resource<?>... outputs) {
        this(List.of(outputs));
    }

    public RenderGraph(List<Resource<?>> outputs) {
        this.outputs = outputs;
        this.outputs.forEach(this::addResourceToGraph);
        this.topologicalSort(outputs);
    }

    //Adds a resources dependency chains to the graph
    private void addResourceToGraph(Resource<?> resource) {
        if (this.graphResources.add(resource)) {
            resource.getDependencyMap().forEach((pass,depends)-> {
                if (pass == null) {
                    throw new IllegalStateException("Pass == null");
                }
                if (depends.contains(null)) {
                    throw new IllegalStateException("Dependency contains null");
                }
                this.dependents.computeIfAbsent(pass, a->new LinkedHashSet<>()).addAll(depends);
            });
        }
    }

    //Note: need to be careful about backrefs e.g. buffers at the end of the graph
    // can be read as input in the next render loop, thus need to process them carefully
    // should probably make this an explicit decleration somehow due to the performance optimizations

    //Note: its possible to depend on "constants" which means that those sections would only get evaluated/executed when the state changes

    //Basicly the goal of the render graph is to only update when/what each nodes inputs change
    // this could be each frame or it could be on chunk update etc

    //The assumption of each pass is that it is pure

    //Also need to make it so that when writing, its not assuming to be a full overwrite but a partial
    // overwrite somehow, that is, reading is dependent on all past writes
    private void topologicalSort(List<Resource<?>> sources) {
        Set<Pass<?>> seen = new HashSet<>();
        List<Pass<?>> ordering = new ArrayList<>();
        for (var source : sources) {
            if (source.getLastWrite() == null) {
                throw new IllegalStateException("Output source has never been written to");
            }
            this.topologicalSort0(seen, source.getLastWrite(), 0, ordering);
        }

        while (true) {
            boolean addedNew = false;
            for (var resource : this.graphResources) {
                var lastRef = resource.getLastWrite();
                if (lastRef == null) {
                    //Resource is only ever read from
                    // it MUST be an external reference
                    if (!(resource instanceof ExternalResource<?, ?>)) {
                        throw new IllegalStateException("Tried reading from a resource thats never written to");
                    }
                    continue;
                }
                if (!seen.contains(lastRef)) {
                    addedNew = true;
                    this.topologicalSort0(seen, lastRef, 0, ordering);
                }
            }

            if (!addedNew) {
                break;
            }
        }


        for (var pass : ordering) {
            pass.verifyAndPrep(this);
        }

        this.ordering.clear();
        this.ordering.addAll(ordering);
    }
    private void topologicalSort0(Set<Pass<?>> seen, Pass<?> current, int depth, List<Pass<?>> sort) {
        //TODO: want to actually find the range in which the phases can run
        // or somehow want to optimize/reduce the amount of memory needed to run everything
        // (can reuses buffers)
        if (!seen.add(current))
            return;

        current.uses().forEach(this::addResourceToGraph);

        for (var ref : this.dependents.get(current)) {
            this.topologicalSort0(seen, ref, depth + 1, sort);
        }

        sort.add(current);
    }
}
