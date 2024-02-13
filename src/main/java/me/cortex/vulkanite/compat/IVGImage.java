package me.cortex.vulkanite.compat;

import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.memory.VGImage;

public interface IVGImage {
    VRef<VGImage> getVGImage();
    void setVGImage(VRef<VGImage> image);
}
