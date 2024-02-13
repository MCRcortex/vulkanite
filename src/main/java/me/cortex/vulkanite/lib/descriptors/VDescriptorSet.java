package me.cortex.vulkanite.lib.descriptors;

import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VRef;

import java.util.HashMap;
import java.util.Map;

public class VDescriptorSet extends VObject {
    private final VDescriptorPool pool;
    public final long poolHandle;
    public final long set;

    public Map<Integer, VRef<VObject>> refs = new HashMap<>();

    protected VDescriptorSet(VDescriptorPool pool, long poolHandle, long set) {
        this.pool = pool;
        this.poolHandle = poolHandle;
        this.set = set;
    }

    @Override
    protected void free() {
        pool.freeSet(this);
    }
}
