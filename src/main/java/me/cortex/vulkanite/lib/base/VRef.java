package me.cortex.vulkanite.lib.base;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.Cleaner;

public class VRef<T extends VObject> {
    private static final Cleaner cleaner = Cleaner.create();

    private final T ref;

    static class State implements Runnable {
        private final VObject ref;

        State(VObject ref) {
            this.ref = ref;
        }

        @Override
        public void run() {
            ref.decRef();
        }
    }

    private final Cleaner.Cleanable cleanable;

    public VRef(T ref) {
        if (ref == null) {
            throw new NullPointerException("VRef to null object");
        }
        ref.incRef();
        this.ref = ref;
        cleanable = cleaner.register(this, new State(ref));
    }

    /**
     * (Optional) Decrement the reference count and release the object if the reference count reaches 0.
     * This method can be called multiple times, but the object will only be released once.
     * If this method is not called, the object will be released when the VRef is garbage collected.
     */
    public void close() {
        cleanable.clean();
    }

    @NotNull
    public T get() {
        return ref;
    }

    @NotNull
    public VRef<T> addRef() {
        return new VRef<>(ref);
    }

    @NotNull
    public VRef<VObject> addRefGeneric() {
        return new VRef<>(ref);
    }
}
