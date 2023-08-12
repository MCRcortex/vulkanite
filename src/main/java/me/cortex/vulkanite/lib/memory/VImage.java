package me.cortex.vulkanite.lib.memory;

public class VImage {
    private final VmaAllocator.ImageAllocation allocation;
    public final int width;
    public final int height;
    public final int mipLayers;
    public final int format;

    VImage(VmaAllocator.ImageAllocation allocation, int width, int height, int mipLayers, int format) {
        this.allocation = allocation;
        this.width = width;
        this.height = height;
        this.mipLayers = mipLayers;
        this.format = format;
    }

    public void free() {
        allocation.free();
    }

    public long image() {
        return allocation.image;
    }
}
