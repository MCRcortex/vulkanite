package me.cortex.vulkanite.client.rendering.srp.lua;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.function.Function;

public class LuaJFunction extends LuaValue {
    private final Function<LuaValue, LuaValue> function;
    public LuaJFunction(Function<LuaValue, LuaValue> function) {
        this.function = function;
    }

    @Override
    public int type() {
        return LuaValue.TLIGHTUSERDATA;
    }

    @Override
    public String typename() {
        return "JavaFunctionProxy";
    }

    @Override
    public LuaValue call(LuaValue arg1) {
        return this.function.apply(arg1);
    }

    @Override
    public LuaValue call() {
        return this.function.apply(null);
    }

    @Override
    public LuaValue invoke(Varargs args) {
        //Cursed, dont do this
        return this.function.apply((LuaValue) args);
    }
}
