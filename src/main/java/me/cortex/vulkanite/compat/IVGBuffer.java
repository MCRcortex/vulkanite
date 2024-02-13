package me.cortex.vulkanite.compat;

import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.memory.VGBuffer;

public interface IVGBuffer {
    VRef<VGBuffer> getBuffer();
    void setBuffer(VRef<VGBuffer> buffer);
}
