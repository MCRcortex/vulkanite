package me.cortex.vulkanite.client.rendering.srp.lua;

import org.luaj.vm2.LuaValue;

public class LuaJObj <T> extends LuaValue {
    private final T object;

    public LuaJObj(T object) {
        this.object = object;
    }

    @Override
    public int type() {
        return LuaValue.TLIGHTUSERDATA;
    }

    @Override
    public String typename() {
        return "JavaObject";
    }

    public T get() {
        return this.object;
    }
}
