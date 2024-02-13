package me.cortex.vulkanite.lib.base;

import java.util.concurrent.atomic.AtomicLong;

public abstract class VObject {
    protected abstract void free();

    private final AtomicLong refCount = new AtomicLong(0);
    protected void incRef() {
        refCount.incrementAndGet();
    }

    protected void decRef() {
        if (refCount.decrementAndGet() == 0) {
            free();
        }
    }
}
