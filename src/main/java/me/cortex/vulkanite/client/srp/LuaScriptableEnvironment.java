package me.cortex.vulkanite.client.srp;

import org.luaj.vm2.LuaValue;

public class LuaScriptableEnvironment extends LuaValue {

    @Override
    public LuaValue get(LuaValue key) {
        System.out.println(key);
        return LuaValue.NIL;
    }

    @Override
    public void set(LuaValue key, LuaValue value) {
        System.out.println(key + ": " + value);
    }

    @Override
    public int type() {
        return TTABLE;
    }

    @Override
    public String typename() {
        return "environment";
    }
}
