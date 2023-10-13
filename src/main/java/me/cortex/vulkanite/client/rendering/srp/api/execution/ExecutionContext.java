package me.cortex.vulkanite.client.rendering.srp.api.execution;

import me.cortex.vulkanite.client.rendering.srp.graph.resource.AccelerationResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.BufferResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalImageResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ImageResource;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.memory.VImage;

//Provides mappings from virtual resource definitions to concrete objects
public class ExecutionContext {
    public VAccelerationStructure getConcreteAcceleration(AccelerationResource resource) {
        return null;
    }

    public VImage getConcreteExternalImage(ExternalImageResource resource) {
        return null;
    }

    public VImage getConcreteImage(ImageResource resource) {
        return null;
    }

    public VBuffer getConcreteBuffer(BufferResource resource) {
        return null;
    }

    public VBuffer getConcreteExternalBuffer(BufferResource resource) {
        return null;
    }
}
