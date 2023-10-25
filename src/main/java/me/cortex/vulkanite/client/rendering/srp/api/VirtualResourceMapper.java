package me.cortex.vulkanite.client.rendering.srp.api;

import me.cortex.vulkanite.client.rendering.srp.api.concreate.MutConcreteBufferInfo;
import me.cortex.vulkanite.client.rendering.srp.api.concreate.MutConcreteImageInfo;
import me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutCache;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.Pass;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.BufferResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ImageResource;

public interface VirtualResourceMapper {
    MutConcreteImageInfo getConcreteResourceBindingInfo(ImageResource resource, Pass<?> pass);

    MutConcreteBufferInfo getConcreteResourceBindingInfo(BufferResource resource, Pass<?> pass);

    LayoutCache getLayoutCacheOrNull();
}
