package me.cortex.vulkanite.lib.memory;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.VRef;

import java.util.Stack;

import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;

public class PoolLinearAllocator {
    private final int usage;
    private final int properties;
    private final int vmaFlags;
    private final long poolSize;
    private final long alignment;

    private VRef<VBuffer> buffer;
    private long currentOffset;

    private final VContext ctx;

    public record BufferRegion(VRef<VBuffer> buffer, long offset, long size, long deviceAddress) { }

    public PoolLinearAllocator(VContext ctx, int usage, long poolSize, long alignment) {
        this(ctx, usage, poolSize, alignment, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
    }

    public PoolLinearAllocator(VContext ctx, int usage, long poolSize, long alignment, int properties, int vmaFlags) {
        this.ctx = ctx;
        this.usage = usage;
        this.poolSize = poolSize;
        this.alignment = alignment;
        this.properties = properties;
        this.vmaFlags = vmaFlags;

        this.currentOffset = 0;

        newBuffer(poolSize);
    }

    private void newBuffer(long size) {
        var newBuffer = ctx.memory.createBuffer(size, usage, properties, alignment, 0);
        if (buffer != null) {
            buffer.close();
        }
        buffer = newBuffer;
        currentOffset = 0;
    }

    public BufferRegion allocate(long size) {
        if (currentOffset + size > poolSize) {
            newBuffer(Long.max(poolSize, size));
        }

        long deviceAddress = buffer.get().hasDeviceAddress() ? buffer.get().deviceAddress() + currentOffset : 0;
        BufferRegion region = new BufferRegion(buffer, currentOffset, size, deviceAddress);
        currentOffset = (currentOffset + size + alignment - 1) & ~(alignment - 1);

        return region;
    }

    public void reset() {
        if (buffer != null) {
            buffer.close();
        }
        currentOffset = 0;
    }
}
