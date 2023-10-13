package me.cortex.vulkanite.client.rendering.srp;

import me.cortex.vulkanite.client.rendering.srp.api.PipelineFactory;
import me.cortex.vulkanite.client.rendering.srp.graph.RenderGraph;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.memory.BufferCopyPass;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline.ComputePass;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline.TracePass;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.BufferResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalImageResource;

public class test {
    public static void main(String[] args) {
        var graph = new RenderGraph(build(new PipelineFactory(), new SRPContext()));
    }

    private static ExternalImageResource build(PipelineFactory factory, SRPContext ctx) {

        var acceleration = ctx.getAccelerationStructure("world");

        var ppmi = new BufferResource()
                .name("Per-pixel material info");

        var dsf = new BufferResource()
                .name("Diffuse specular reflection");

        new TracePass(factory.createTracePipeline())
                .name("Raytrace")
                .bindLayout(acceleration, ppmi, dsf)
                .bindLayout(1, ctx.getExternalLayout("world-trace-geometries"));

        var psr = new BufferResource()
                .name("Previous specular reflection");

        var pfd = new BufferResource()
                .name("Previous filtered diffuse");

        var td = new BufferResource()
                .name("temporal diffuse");

        var tsr = new BufferResource()
                .name("temporal specular reflection");

        new ComputePass(factory.createComputePipeline())
                .name("Temporal Re-projection")
                .bindLayout(ppmi, dsf, pfd, psr, td, tsr);

        var fd = new BufferResource()
                .name("filtered diffuse");

        new ComputePass(factory.createComputePipeline())
                .name("SVGF first pass")
                .bindLayout(td, fd);

        new BufferCopyPass(tsr, psr)
                .name("Copy temporal specular reflection");

        new BufferCopyPass(fd, pfd)
                .name("Copy filtered diffuse");

        var sd = new BufferResource()
                .name("SVGF pass: " + 0);

        var svgfComputePipeline = factory.createComputePipeline();
        for (int i = 0; i < 4; i++) {
            new ComputePass(svgfComputePipeline)
                    .name("SVGF pass: " + i)
                    .bindLayout(fd, sd);
            fd = sd;
            sd = new BufferResource()
                    .name("SVGF buffer: " + (i+1));
        }
        sd = fd;

        var lighting = new BufferResource()
                .name("lighting");

        new ComputePass(factory.createComputePipeline())
                .name("Combination pass")
                .bindLayout(tsr, sd, lighting);

        var output = ctx.getExternalTexture("colortex0");

        new ComputePass(factory.createComputePipeline())
                .name("Post processing")
                .bindLayout(lighting, output);

        return output;
    }
}
