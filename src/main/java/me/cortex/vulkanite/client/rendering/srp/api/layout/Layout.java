package me.cortex.vulkanite.client.rendering.srp.api.layout;

import java.util.ArrayList;
import java.util.List;

public class Layout {
    private final List<LayoutBinding> bindings = new ArrayList<>();

    public List<LayoutBinding> getBindings() {
        return bindings;
    }

}
