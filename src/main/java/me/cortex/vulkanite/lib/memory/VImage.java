package me.cortex.vulkanite.lib.memory;

import java.lang.ref.Cleaner;

public class VImage {
    private static final Cleaner cc = Cleaner.create();

    private VmaAllocator.ImageAllocation allocation;
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
        cc.register(this, ()->{
            if (!allocation.freed) {
                System.err.println("Image memory leak");
            }
        });
    }

    public void free() {
        allocation.free();
        allocation = null;
    }

    public long image() {
        return allocation.image;
    }
}
