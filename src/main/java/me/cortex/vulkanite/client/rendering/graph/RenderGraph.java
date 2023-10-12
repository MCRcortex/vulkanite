package me.cortex.vulkanite.client.rendering.graph;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import me.cortex.vulkanite.client.rendering.graph.phase.Pass;
import me.cortex.vulkanite.client.rendering.graph.resource.Resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RenderGraph {
    private final Reference2ObjectMap<Pass<?>, List<Pass<?>>> dependents = new Reference2ObjectOpenHashMap<>();
    private final List<Resource<?>> outputs;

    public RenderGraph(Resource<?>... outputs) {
        this(List.of(outputs));
    }

    public RenderGraph(List<Resource<?>> outputs) {
        this.outputs = outputs;
        this.setupGraph();
        this.topologicalSort(outputs);
    }

    //Sets up the dependency graph from the input set of resources
    private void setupGraph() {

    }

    //Returns the pass that writes to a resource last, if its never written to, return null
    private Pass<?> getLastWrite(Resource<?> resource) {
        return null;
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
            this.topologicalSort0(seen, this.getLastWrite(source), 0, ordering);
        }
        //TODO: need to compute backrefs here and run another topological sort on them to extend this sort
        // that is, any variables where the first operation is a read is a backref, so need to find the
        // last write phase and be dependent on that
    }
    private void topologicalSort0(Set<Pass<?>> seen, Pass<?> current, int depth, List<Pass<?>> sort) {
        //TODO: want to actually find the range in which the phases can run
        // or somehow want to optimize/reduce the amount of memory needed to run everything
        // (can reuses buffers)
        if (!seen.add(current))
            return;

        for (var ref : this.dependents.get(seen)) {
            this.topologicalSort0(seen, ref, depth + 1, sort);
        }

        sort.add(current);
    }
}
