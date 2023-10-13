package me.cortex.vulkanite.client.rendering.srp;

import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.AccelerationResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalBoundLayout;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalImageResource;

public class SRPContext {
    public ExternalBoundLayout getExternalLayout(String identifier) {
        return null;// new ExternalBoundLayout(new Layout(new LayoutBinding(1))).name(identifier);
    }

    public AccelerationResource getAccelerationStructure(String identifier) {
        return new AccelerationResource().name(identifier);
    }

    public ExternalImageResource getExternalTexture(String identifier) {
        return (ExternalImageResource) new ExternalImageResource().name(identifier);
    }
}
