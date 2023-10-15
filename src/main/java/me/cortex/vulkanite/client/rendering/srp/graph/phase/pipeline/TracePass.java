package me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline;

import me.cortex.vulkanite.client.rendering.srp.api.execution.ExecutionContext;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.TracePipeline;
import me.cortex.vulkanite.lib.pipeline.VRaytracePipeline;
import org.joml.Vector3i;

import java.util.function.Supplier;

public class TracePass extends PipelinePass<TracePass, TracePipeline, VRaytracePipeline> {
    private final Supplier<Vector3i> dispatchSizeSupplier;

    public TracePass(TracePipeline pipeline, Supplier<Vector3i> dispatchSizeSupplier) {
        super(pipeline);
        this.dispatchSizeSupplier = dispatchSizeSupplier;
    }

    @Override
    public void execute(ExecutionContext ctx) {
        var pipe = this.pipeline.getConcretePipeline();
        var size = this.dispatchSizeSupplier.get();
        pipe.bind(ctx.cmd);
        pipe.bindDSets(ctx.cmd, this.getDescriptorSets(ctx.cmd));
        pipe.trace(ctx.cmd, size.x, size.y, size.z);
    }
}

