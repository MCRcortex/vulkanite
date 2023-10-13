package me.cortex.vulkanite.client.rendering.srp.api.layout;

import java.util.ArrayList;
import java.util.List;

public class Layout {
    private final List<LayoutBinding> bindings = new ArrayList<>();
    private final boolean runtimeSized;

    public Layout(boolean runtimeSized, LayoutBinding... bindings) {
        this.runtimeSized = runtimeSized;
        this.bindings.addAll(List.of(bindings));
    }

    public List<LayoutBinding> getBindings() {
        return this.bindings;
    }

}
