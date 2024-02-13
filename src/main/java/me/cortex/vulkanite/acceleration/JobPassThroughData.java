package me.cortex.vulkanite.acceleration;

import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;

import java.util.List;

public record JobPassThroughData(RenderSection section, long time, List<VRef<VBuffer>> geometryBuffers) {

}
