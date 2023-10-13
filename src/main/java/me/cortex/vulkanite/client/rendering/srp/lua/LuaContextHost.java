package me.cortex.vulkanite.client.rendering.srp.lua;

import me.cortex.vulkanite.client.rendering.srp.graph.resource.AccelerationResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.BufferResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalImageResource;
import org.luaj.vm2.*;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.luajc.JavaLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.function.Function;

import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;

public class LuaContextHost {
    private final LuaFunctions functions = new LuaFunctions(this);
    private final Function<String, byte[]> resourceLoader;
    private final JavaLoader loader = new JavaLoader();
    public LuaContextHost(Function<String, byte[]> resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    private LuaFunction compileBytecode(byte[] source, String classname, LuaValue globals) {
        return compileBytecode(compile(source), classname, globals);
    }

    private static Prototype compile(byte[] source) {
        try {
            return LuaC.instance.compile(new ByteArrayInputStream(source), "script");
        } catch (
                IOException e) {
            throw new RuntimeException(e);
        }
    }

    private LuaFunction compileBytecode(Prototype prototype, String classname, LuaValue globals) {
        return this.loader.load(prototype, classname, "script", globals);
    }

    public LuaValue loadRunScript(String name) {
        var func = compileBytecode(this.resourceLoader.apply(name), "me.cortex.vulkanite.client.srp.runtime.UserPipeline", createStandardLibrary());
        return func.call();
    }

    private LuaValue createStandardLibrary() {
        var env = new LuaTable();
        env.set("ctx", createContextTable());
        env.set("SHADER_RAY_GEN", VK_SHADER_STAGE_RAYGEN_BIT_KHR);
        env.set("SHADER_RAY_MISS", VK_SHADER_STAGE_MISS_BIT_KHR);
        env.set("SHADER_RAY_CHIT", VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR);
        env.set("SHADER_RAY_AHIT", VK_SHADER_STAGE_ANY_HIT_BIT_KHR);
        env.set("SHADER_RAY_INTER", VK_SHADER_STAGE_INTERSECTION_BIT_KHR);
        env.set("ShaderModule", new LuaJFunction(this.functions::ShaderModule));
        env.set("RaytracePipeline", new LuaJFunction(this.functions::RaytracePipeline));
        env.set("RaytracePass", new LuaJFunction(this.functions::RaytracePass));
        env.set("setOutput", new LuaJFunction(this.functions::setOutput));
        return env;
    }

    private LuaValue createContextTable() {
        var ctx = new LuaTable();
        ctx.set("commonUniformBuffer", new LuaJObj<>(new BufferResource()));
        ctx.set("accelerationStructure", new LuaJObj<>(new AccelerationResource()));
        ctx.set("blockAtlas", new LuaJObj<>(new ExternalImageResource()));
        ctx.set("blockAtlasNormal", new LuaJObj<>(new ExternalImageResource()));
        ctx.set("blockAtlasSpecular", new LuaJObj<>(new ExternalImageResource()));
        ctx.set("irisOutputTextures", LuaValue.listOf(new LuaValue[]{new LuaJObj<>(new ExternalImageResource()),new LuaJObj<>(new ExternalImageResource())}));
        return ctx;
    }

    public byte[] getResource(String path) {
        return this.resourceLoader.apply(path);
    }
}
