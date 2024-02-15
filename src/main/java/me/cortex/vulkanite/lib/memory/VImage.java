package me.cortex.vulkanite.lib.memory;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VRef;

import java.lang.ref.Cleaner;

import static org.lwjgl.vulkan.VK10.VK_OBJECT_TYPE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_OBJECT_TYPE_IMAGE;

public class VImage extends VObject {
    protected VmaAllocator.ImageAllocation allocation;
    public final int width;
    public final int height;
    public final int depth;
    public final int mipLayers;
    public final int format;

    public final int dimensions;

    protected VImage(VmaAllocator.ImageAllocation allocation, int width, int height, int depth, int mipLayers, int format) {
        this.allocation = allocation;
        this.width = width;
        this.height = height;
        this.mipLayers = mipLayers;
        this.format = format;
        this.depth = depth;

        int dimensions = 3;

        if (height == 1 && depth == 1) {
            dimensions = 1;
        }
        else if(height != 1 && depth == 1) {
            dimensions = 2;
        }

        this.dimensions = dimensions;
    }

    public static VRef<VImage> create(VmaAllocator.ImageAllocation allocation, int width, int height, int depth, int mipLayers, int format) {
        return new VRef<>(new VImage(allocation, width, height, depth, mipLayers, format));
    }

    protected void free() {
        allocation.free();
        allocation = null;
    }

    public long image() {
        return allocation.image;
    }

    public void setDebugUtilsObjectName(String name) {
        Vulkanite.INSTANCE.getCtx().setDebugUtilsObjectName(image(), VK_OBJECT_TYPE_IMAGE, name);
    }
}
