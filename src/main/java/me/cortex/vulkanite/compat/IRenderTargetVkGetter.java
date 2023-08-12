package me.cortex.vulkanite.compat;

import me.cortex.vulkanite.lib.memory.VGImage;

public interface IRenderTargetVkGetter {
    VGImage getMain();
    VGImage getAlt();
}
