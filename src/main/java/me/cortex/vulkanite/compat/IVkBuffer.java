package me.cortex.vulkanite.compat;

import me.cortex.vulkanite.lib.memory.VGBuffer;

public interface IVkBuffer {
    VGBuffer getBuffer();
    void setBuffer(VGBuffer buffer);
}
