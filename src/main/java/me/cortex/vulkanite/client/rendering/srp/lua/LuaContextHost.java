package me.cortex.vulkanite.client.rendering.srp.lua;

import me.cortex.vulkanite.client.rendering.srp.api.execution.ExternalResourceTracker;
import me.cortex.vulkanite.client.rendering.srp.graph.RenderGraph;
import org.luaj.vm2.*;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.jse.JseMathLib;
import org.luaj.vm2.lib.jse.JseStringLib;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;

import static me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding.ACCESS_READ;
import static me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding.ACCESS_WRITE;
import static me.cortex.vulkanite.client.rendering.srp.lua.LuaExternalObjects.*;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;
import static org.lwjgl.vulkan.VK10.*;

public class LuaContextHost {
    private final LuaFunctions functions = new LuaFunctions(this);
    private final Function<String, byte[]> resourceLoader;
    private final LuaJavaLoader loader = new LuaJavaLoader(LuaContextHost.class.getClassLoader());
    private LuaFunction generationFunction;
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

    private static int UNIQUEIFIER = 0;
    public void loadScript(String name) {
        this.generationFunction = compileBytecode(this.resourceLoader.apply(name), "me.cortex.vulkanite.client.srp.runtime.UserPipeline"+(UNIQUEIFIER++), createStandardLibrary());
    }

    public void run() {
        //Need to reset the graph, else it pulls in things from previous invocations that are not valid
        // TODO: to fix this properly, need to make LuaExternalObjects an actual object not public static final
        LuaExternalObjects.resetExternalObjectGraph();
        ExternalResourceTracker.resetAllCallbacks();

        this.generationFunction.call();
    }


    private void addLuaStdLib(LuaTable table) {
        new Bit32Lib().call(LuaValue.valueOf("bit32"), table);
        new JseMathLib().call(LuaValue.valueOf("math"), table);
        new JseStringLib().call(LuaValue.valueOf("string"), table);
        new TableLib().call(LuaValue.valueOf("table"), table);
    }

    private LuaValue createStandardLibrary() {
        var env = new LuaTable();
        addLuaStdLib(env);
        LuaConstants.addConstants(env);
        env.set("ctx", createContextTable());
        env.set("Buffer", new LuaJFunction(this.functions::Buffer));
        env.set("Image", new LuaJFunction(this.functions::Image));
        env.set("Layout", new LuaJFunction(this.functions::Layout));
        env.set("ShaderModule", new LuaJFunction(this.functions::ShaderModule));
        env.set("RaytracePipeline", new LuaJFunction(this.functions::RaytracePipeline));
        env.set("RaytracePass", new LuaJFunction(this.functions::RaytracePass));
        env.set("ComputePipeline", new LuaJFunction(this.functions::ComputePipeline));
        env.set("ComputePass", new LuaJFunction(this.functions::ComputePass));
        env.set("setOutput", new LuaJFunction(this.functions::setOutput));
        env.set("loadResource", new LuaJFunction(this::loadResource));
        return env;
    }

    private LuaValue loadResource(LuaValue arg) {
        var resource = resourceLoader.apply(arg.checkjstring());
        return resource==null?LuaValue.NIL:LuaString.valueUsing(resource);
    }

    private LuaValue createContextTable() {
        var ctx = new LuaTable();
        ctx.set("commonUniformBuffer", new LuaJObj<>(COMMON_UNIFORM_BUFFER));
        ctx.set("accelerationStructure", new LuaJObj<>(WORLD_ACCELERATION_STRUCTURE));
        ctx.set("blockAtlas", new LuaJObj<>(BLOCK_ATLAS));
        ctx.set("blockAtlasNormal", new LuaJObj<>(BLOCK_ATLAS_NORMAL));
        ctx.set("blockAtlasSpecular", new LuaJObj<>(BLOCK_ATLAS_SPECULAR));
        ctx.set("irisOutputTextures", LuaValue.listOf(Arrays.stream(IRIS_IMAGES).map(LuaJObj::new).toArray(LuaValue[]::new)));
        ctx.set("terrainGeometrySet", new LuaJObj<>(TERRAIN_GEOMETRY_LAYOUT));
        ctx.set("entityDataSet", new LuaJObj<>(ENTITY_DATA_LAYOUT));
        return ctx;
    }

    public byte[] getResource(String path) {
        return this.resourceLoader.apply(path);
    }

    public RenderGraph getGraph() {
        return this.functions.graph;
    }

    public void destory() {
        this.functions.freeObjects();
    }
}
