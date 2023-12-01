package me.cortex.vulkanite.client.rendering.srp.api.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Layout {
    private final List<LayoutBinding> bindings = new ArrayList<>();
    private final int hash;

    public Layout(LayoutBinding... bindings) {
        this(List.of(bindings));
    }

    public Layout(List<LayoutBinding> bindings) {
        this.bindings.addAll(bindings);
        this.hash = Objects.hash(this.bindings);
    }

    public List<LayoutBinding> getBindings() {
        return this.bindings;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        Layout layout = (Layout) object;
        return this.hash == layout.hash && Objects.equals(bindings, layout.bindings);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    public boolean hasUnsizedArrays() {
        return this.bindings.stream().anyMatch(a->a.arraySize==-1);
    }
}
