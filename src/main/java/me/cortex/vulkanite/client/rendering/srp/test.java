package me.cortex.vulkanite.client.rendering.srp;

import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding;
import me.cortex.vulkanite.client.rendering.srp.graph.RenderGraph;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.memory.BufferCopyPass;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline.ComputePass;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline.TracePass;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.BufferResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalImageResource;

public class test {
    /*
    public static void main(String[] args) {
        RenderGraph graph = null;
        ExternalImageResource out = null;
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1_000_000; i++) {
            out = build(new PipelineFactory(), new SRPContext());
            graph = new RenderGraph(out);
        }
        System.err.println(out + " " + graph);
        System.err.println(System.currentTimeMillis()-start);
    }

    private static ExternalImageResource build(PipelineFactory factory, SRPContext ctx) {

        var acceleration = ctx.getAccelerationStructure("world");

        var ppmi = new BufferResource()
                .name("Per-pixel material info");

        var dsf = new BufferResource()
                .name("Diffuse specular reflection");

        var geometryLayout = ctx.getExternalLayout("world-trace-geometries");
        new TracePass(factory.createTracePipeline(new Layout(new LayoutBinding(1), new LayoutBinding(2), new LayoutBinding(2)), geometryLayout.layout()))
                .name("Raytrace")
                .bindLayout(acceleration, ppmi, dsf)
                .bindLayout(1, geometryLayout);

        var psr = new BufferResource()
                .name("Previous specular reflection");

        var pfd = new BufferResource()
                .name("Previous filtered diffuse");

        var td = new BufferResource()
                .name("temporal diffuse");

        var tsr = new BufferResource()
                .name("temporal specular reflection");

        new ComputePass(factory.createComputePipeline(new Layout(new LayoutBinding(1), new LayoutBinding(1), new LayoutBinding(1), new LayoutBinding(1), new LayoutBinding(2), new LayoutBinding(2))))
                .name("Temporal Re-projection")
                .bindLayout(ppmi, dsf, pfd, psr, td, tsr);

        var fd = new BufferResource()
                .name("filtered diffuse");

        new ComputePass(factory.createComputePipeline(new Layout(new LayoutBinding(1), new LayoutBinding(2))))
                .name("SVGF first pass")
                .bindLayout(td, fd);

        new BufferCopyPass(tsr, psr)
                .name("Copy temporal specular reflection");

        new BufferCopyPass(fd, pfd)
                .name("Copy filtered diffuse");

        var sd = new BufferResource()
                .name("SVGF pass: " + 0);

        var svgfComputePipeline = factory.createComputePipeline(new Layout(new LayoutBinding(1), new LayoutBinding(2)));
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

        new ComputePass(factory.createComputePipeline(new Layout(new LayoutBinding(1), new LayoutBinding(1), new LayoutBinding(1), new LayoutBinding(2))))
                .name("Combination pass")
                .bindLayout(ppmi, tsr, sd, lighting);

        var output = ctx.getExternalTexture("colortex0");

        new ComputePass(factory.createComputePipeline(new Layout(new LayoutBinding(1), new LayoutBinding(2))))
                .name("Post processing")
                .bindLayout(lighting, output);

        return output;
    }*/
}
