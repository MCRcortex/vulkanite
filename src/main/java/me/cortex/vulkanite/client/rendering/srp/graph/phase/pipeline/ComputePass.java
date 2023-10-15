package me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline;

import me.cortex.vulkanite.client.rendering.srp.api.execution.ExecutionContext;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.ComputePipeline;
import me.cortex.vulkanite.lib.pipeline.VComputePipeline;
import org.joml.Vector3i;

import java.util.function.Supplier;

public class ComputePass extends PipelinePass<ComputePass, ComputePipeline, VComputePipeline> {
    private final Supplier<Vector3i> dispatchSizeSupplier;

    public ComputePass(ComputePipeline pipeline, Supplier<Vector3i> dispatchSizeSupplier) {
        super(pipeline);
        this.dispatchSizeSupplier = dispatchSizeSupplier;
    }

    @Override
    public void execute(ExecutionContext ctx) {
        var pipe = this.pipeline.getConcretePipeline();
        var size = this.dispatchSizeSupplier.get();
        pipe.bind(ctx.cmd);
        pipe.bindDSets(ctx.cmd, this.getDescriptorSets(ctx.cmd));
        pipe.dispatch(ctx.cmd, size.x, size.y, size.z);
    }
}
