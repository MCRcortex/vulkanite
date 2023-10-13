package me.cortex.vulkanite.client.rendering.srp.graph.phase.memory;

import me.cortex.vulkanite.client.rendering.srp.graph.resource.BufferResource;

public class BufferCopyPass extends CopyPass<BufferCopyPass> {
    public BufferCopyPass(BufferResource from, BufferResource too) {
        this.writes(too);
        this.reads(from);
    }
}
