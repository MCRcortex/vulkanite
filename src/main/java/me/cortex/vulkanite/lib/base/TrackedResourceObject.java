package me.cortex.vulkanite.lib.base;

import java.lang.ref.Cleaner;

public abstract class TrackedResourceObject {

    private final Ref ref;
    public TrackedResourceObject() {
        this.ref = register(this);
    }

    protected void free0() {
        ref.freedRef[0] = true;
        ref.cleanable.clean();
    }

    public abstract void free();

    public void assertNotFreed() {
        if (isFreed()) {
            throw new IllegalStateException("Object " + this + " should not be free, but is");
        }
    }

    public boolean isFreed() {
        return ref.freedRef[0];
    }

    private record Ref(Cleaner.Cleanable cleanable, boolean[] freedRef) {}

    private static final Cleaner cleaner = Cleaner.create();
    public static Ref register(Object obj) {
        String clazz = obj.getClass().getName();
        Throwable trace = new Throwable();
        boolean[] freed = new boolean[1];
        var clean = cleaner.register(obj, ()->{
            if (!freed[0]) {
                System.err.println("Object named: "+ clazz+" was not freed, location at: ");
                trace.printStackTrace();
            }
        });
        return new Ref(clean, freed);
    }
}
