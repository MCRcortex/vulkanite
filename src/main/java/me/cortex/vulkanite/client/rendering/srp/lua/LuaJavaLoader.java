package me.cortex.vulkanite.client.rendering.srp.lua;

import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.luajc.JavaGen;

import java.util.HashMap;
import java.util.Map;

public class LuaJavaLoader extends ClassLoader {

    private final Map<String, byte[]> unloaded = new HashMap<>();

    public LuaJavaLoader(ClassLoader parent) {
        super(parent);
    }

    public LuaFunction load(Prototype p, String classname, String filename, LuaValue env) {
        JavaGen jg = new JavaGen(p, classname, filename, false);
        return load(jg, env);
    }

    public LuaFunction load(JavaGen jg, LuaValue env) {
        include(jg);
        return load(jg.classname, env);
    }

    public LuaFunction load(String classname, LuaValue env) {
        try {
            Class<?> c = loadClass(classname);
            LuaFunction v = (LuaFunction) c.newInstance();
            v.initupvalue1(env);
            return v;
        } catch (Exception e) {
            throw new IllegalStateException("bad class gen: " + e);
        }
    }

    public void include(JavaGen jg) {
        unloaded.put(jg.classname, jg.bytecode);
        for (int i = 0, n = jg.inners != null? jg.inners.length: 0; i < n; i++)
            include(jg.inners[i]);
    }

    @Override
    public Class<?> findClass(String classname) throws ClassNotFoundException {
        byte[] bytes = unloaded.get(classname);
        if (bytes != null)
            return defineClass(classname, bytes, 0, bytes.length);
        return super.findClass(classname);
    }
}
