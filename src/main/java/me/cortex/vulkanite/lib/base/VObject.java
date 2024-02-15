package me.cortex.vulkanite.lib.base;

import java.util.concurrent.atomic.AtomicLong;

public abstract class VObject {
    protected abstract void free();

    protected final AtomicLong refCount = new AtomicLong(0);
    protected void incRef() {
        if (refCount.incrementAndGet() == 1) {
            // First reference, put into registry
            // So that the object is kept alive until we finish running `free()`
            VRegistry.INSTANCE.register(this);
        }
    }

    protected void decRef() {
        if (refCount.decrementAndGet() == 0) {
            free();
            VRegistry.INSTANCE.unregister(this);
        }
    }
}
