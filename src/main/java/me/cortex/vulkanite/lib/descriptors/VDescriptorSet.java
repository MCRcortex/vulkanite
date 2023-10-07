package me.cortex.vulkanite.lib.descriptors;

import me.cortex.vulkanite.lib.base.TrackedResourceObject;

public class VDescriptorSet extends TrackedResourceObject {
    private final VTypedDescriptorPool pool;
    public final long poolHandle;
    public final long set;

    VDescriptorSet(VTypedDescriptorPool pool, long poolHandle, long set) {
        this.pool = pool;
        this.poolHandle = poolHandle;
        this.set = set;
    }

    @Override
    public void free() {
        free0();
        pool.freeSet(this);
    }
}
