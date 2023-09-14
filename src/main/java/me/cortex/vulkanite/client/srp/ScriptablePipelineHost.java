package me.cortex.vulkanite.client.srp;

import org.luaj.vm2.*;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.luajc.JavaLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.function.Function;

public class ScriptablePipelineHost {
    private final Function<String, byte[]> resourceLoader;
    public ScriptablePipelineHost(Function<String, byte[]> resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public void load(String path) {
        var proto = compile(resourceLoader.apply(path));
        //new LuaClosure(proto, new LuaTable()).call();
        //System.out.println(proto);
        compileBytecode(proto, "me.cortex.vulkanite.client.srp.runtime.UserPipeline", new LuaTable()).call();
    }

    private static Prototype compile(byte[] source) {
        try {
            return LuaC.instance.compile(new ByteArrayInputStream(source), "script");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static LuaFunction compileBytecode(Prototype prototype, String classname, LuaValue globals) {
        JavaLoader loader = new JavaLoader();
        return loader.load(prototype, classname, "script", globals);
    }
}
