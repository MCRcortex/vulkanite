package me.cortex.vulkanite.client.rendering.srp.graph.phase.memory;

import me.cortex.vulkanite.client.rendering.srp.graph.resource.ImageResource;

public class ImageCopyPass extends CopyPass<ImageCopyPass> {
    public ImageCopyPass(ImageResource from, ImageResource too) {
        this.writes(too);
        this.reads(from);
    }
}
