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

    public VRef(T ref) {
        ref.incRef();
        this.ref = ref;
        cleaner.register(this, new State(ref));
    }

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
