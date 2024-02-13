package me.cortex.vulkanite.compat;

import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.memory.VGImage;

public interface IRenderTargetVkGetter {
    VRef<VGImage> getMain();
    VRef<VGImage> getAlt();
}
