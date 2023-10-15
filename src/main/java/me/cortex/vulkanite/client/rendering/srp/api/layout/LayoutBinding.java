package me.cortex.vulkanite.client.rendering.srp.api.layout;

import java.util.Objects;

public class LayoutBinding {
    public final String name;
    public final int index;
    public final int type;
    public final int access;
    public final int arraySize;

    private final int hash;
    public LayoutBinding(int index, int access, int type, int arraySize) {
        this(null, index, access, type, arraySize);
    }

    public LayoutBinding(String name, int index, int access, int type, int arraySize) {
        this.name = name;
        this.index = index;
        this.access = access;
        this.type = type;
        this.arraySize = arraySize;

        this.hash = Objects.hash(index, access, type, arraySize);
    }

    public boolean writes() {
        return (access&2)!=0;
    }

    public boolean reads() {
        return (access&1)!=0;
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        LayoutBinding that = (LayoutBinding) object;
        return hash == that.hash && index == that.index && type == that.type && access == that.access && arraySize == that.arraySize;
    }

    public int type() {
        return this.type;
    }
}
