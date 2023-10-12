package me.cortex.vulkanite.client.rendering.graph;

import me.cortex.vulkanite.client.rendering.graph.phase.BufferCopyPass;
import me.cortex.vulkanite.client.rendering.graph.phase.ComputePass;
import me.cortex.vulkanite.client.rendering.graph.phase.TracePass;
import me.cortex.vulkanite.client.rendering.graph.resource.BufferResource;
import me.cortex.vulkanite.client.rendering.graph.resource.ImageResource;

import java.util.ArrayList;

public class test {
    public static void main(String[] args) {
        var ppmi = new BufferResource()
                .name("Per-pixel material info");

        var dsf = new BufferResource()
                .name("Diffuse specular reflection");

        new TracePass()
                .name("Raytrace")
                .writes(ppmi)
                .writes(dsf);

        var psr = new BufferResource()
                .name("Previous specular reflection");

        var pfd = new BufferResource()
                .name("Previous filtered diffuse");

        var td = new BufferResource()
                .name("temporal diffuse");

        var tsr = new BufferResource()
                .name("temporal specular reflection");

        new ComputePass()
                .name("Temporal Re-projection")
                .reads(ppmi)
                .reads(dsf)
                .reads(pfd)
                .reads(psr)
                .writes(td)
                .writes(tsr);

        var fd = new BufferResource()
                .name("filtered diffuse");

        new ComputePass()
                .name("SVGF first pass")
                .reads(td)
                .writes(fd);

        new BufferCopyPass(tsr, psr)
                .name("Copy temporal specular reflection");

        new BufferCopyPass(fd, pfd)
                .name("Copy filtered diffuse");

        var sd = new BufferResource()
                .name("Smooth diffuse");

        new ComputePass()
                .name("SVGF passes")
                .reads(fd)
                .writes(sd);

        var lighting = new BufferResource()
                .name("lighting");

        new ComputePass()
                .name("Combination pass")
                .reads(tsr)
                .reads(sd)
                .writes(lighting);

        var output = new ImageResource()
                .name("output texture (external from iris)");

        new ComputePass()
                .name("Post processing")
                .reads(lighting)
                .writes(output);


        var graph = new RenderGraph(output);
    }
}
