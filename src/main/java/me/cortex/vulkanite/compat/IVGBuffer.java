package me.cortex.vulkanite.compat;

import me.cortex.vulkanite.lib.memory.VGBuffer;

public interface IVGBuffer {
    VGBuffer getBuffer();
    void setBuffer(VGBuffer buffer);
}
