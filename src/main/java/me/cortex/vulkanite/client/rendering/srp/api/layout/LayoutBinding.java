package me.cortex.vulkanite.client.rendering.srp.api.layout;

public class LayoutBinding {
    private final String name;
    private final int index;
    private final int type;
    private final int access;
    private final int arraySize;

    public LayoutBinding(int index, int access, int type, int arraySize) {
        this(null, index, access, type, arraySize);
    }

    public LayoutBinding(String name, int index, int access, int type, int arraySize) {
        this.name = name;
        this.index = index;
        this.access = access;
        this.type = type;
        this.arraySize = arraySize;
    }

    public boolean writes() {
        return (access&2)!=0;
    }

    public boolean reads() {
        return (access&1)!=0;
    }
}
