package me.cortex.vulkanite.client.rendering.srp.lua;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.srp.api.execution.ExecutionConstants;
import me.cortex.vulkanite.client.rendering.srp.api.execution.ExecutionContext;
import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.ComputePipeline;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.TracePipeline;
import me.cortex.vulkanite.client.rendering.srp.graph.RenderGraph;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline.ComputePass;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline.TracePass;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.BufferResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalBoundLayout;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ImageResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.Resource;
import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.pipeline.ComputePipelineBuilder;
import me.cortex.vulkanite.lib.pipeline.RaytracePipelineBuilder;
import me.cortex.vulkanite.lib.shader.ShaderModule;
import me.cortex.vulkanite.lib.shader.VShader;
import me.cortex.vulkanite.lib.shader.reflection.ShaderReflection;
import org.joml.Vector3i;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.*;

//TODO: create a set of vk resources that need to get freed
public class LuaFunctions {
    private final Set<TrackedResourceObject> allocatedObjects = new HashSet<>();

    private final VContext vctx = Vulkanite.INSTANCE.getCtx();
    private final LuaContextHost ctx;
    public RenderGraph graph;

    public LuaFunctions(LuaContextHost ctx) {
        this.ctx = ctx;
    }

    public LuaValue Buffer(LuaValue arg) {
        return new LuaJObj<>(new BufferResource());
    }

    public LuaValue Image(LuaValue arg) {
        return new LuaJObj<>(new ImageResource());
    }

    public LuaValue ShaderModule(LuaValue arg) {
        int stages = arg.get("type").checkint();
        VShader shader = null;
        if (arg.get("file") != LuaValue.NIL) {
            shader = VShader.compileLoad(vctx, new String(ctx.getResource(arg.get("file").checkjstring())), stages);
        } else if (arg.get("src") != LuaValue.NIL) {
            shader = VShader.compileLoad(vctx, arg.get("src").checkjstring(), stages);
        } else if (arg.get("spirv") != LuaValue.NIL) {
            var strObj = arg.get("spirv").checkstring();
            //TODO: fixme, verify that this cant cause an out of bounds memory write
            var spirvBuffer = MemoryUtil.memAlloc(strObj.m_length);
            spirvBuffer.put(0, strObj.m_bytes, strObj.m_offset, strObj.m_length);
            spirvBuffer.rewind();
            shader = VShader.compileLoad(vctx, spirvBuffer, stages);
            MemoryUtil.memFree(spirvBuffer);
        } else {
            throw new IllegalArgumentException("Unknown loading method for args: " + arg);
        }

        this.allocatedObjects.add(shader);
        return new LuaJObj<>(shader);
    }

    private static ShaderModule getShaderModule(LuaValue value) {
        if (value instanceof LuaJObj<?> obj) {
            //Assume since its a raw object, just use the default named module
            return ((LuaJObj<VShader>)obj).get().named();
        }
        var module = ((LuaJObj<VShader>)value.get("module")).get();
        var name = value.get("entry").checkjstring();
        return module.named(name);
    }

    public LuaValue RaytracePipeline(LuaValue arg) {
        var builder = new RaytracePipelineBuilder();


        var rgen = getShaderModule(arg.get("raygen"));
        builder.setRayGen(rgen);

        var missShaders = arg.get("raymiss").checktable();
        for (int index = 1; index < missShaders.keyCount()+1; index++) {
            var rmiss = getShaderModule(missShaders.get(index));
            builder.addMiss(rmiss);
        }

        var hitShaders = arg.get("rayhit").checktable();
        for (int index = 1; index < hitShaders.keyCount()+1; index++) {
            var hitEntry = hitShaders.get(index).checktable();
            ShaderModule chit = null;
            ShaderModule ahit = null;
            ShaderModule ihit = null;
            if (hitEntry.get("close") != LuaValue.NIL) {
                chit = getShaderModule(hitEntry.get("close"));
            }
            if (hitEntry.get("any") != LuaValue.NIL) {
                ahit = getShaderModule(hitEntry.get("any"));
            }
            if (hitEntry.get("intersect") != LuaValue.NIL) {
                ihit = getShaderModule(hitEntry.get("intersect"));
            }
            builder.addHit(chit, ahit, ihit);
        }
        var pipeline = builder.build(vctx, 1);
        this.allocatedObjects.add(pipeline);
        return new LuaJObj<>(new TracePipeline(pipeline, pipeline.reflection.getSets().stream().map(LuaFunctions::buildLayout).toList()));
    }

    public LuaValue ComputePipeline(LuaValue arg) {
        var builder = new ComputePipelineBuilder();

        var shader = ((LuaJObj<VShader>)arg.get("shader")).get().named();
        builder.set(shader);

        var pipeline = builder.build(vctx);
        this.allocatedObjects.add(pipeline);
        return new LuaJObj<>(new ComputePipeline(pipeline, shader.shader().getReflection().getSets().stream().map(LuaFunctions::buildLayout).toList()));
    }

    private static Layout buildLayout(ShaderReflection.Set set) {
        List<LayoutBinding> bindingList = new ArrayList<>();
        for (var binding : set.bindings()) {
            bindingList.add(new LayoutBinding(binding.name(), binding.binding(), binding.accessMsk(), binding.descriptorType(), binding.runtimeSized()?-1:binding.arraySize()));
        }
        return new Layout(bindingList.toArray(LayoutBinding[]::new));
    }

    private static List<List<Resource<?>>> generateBindingList(LuaTable bindingArrayList) {
        List<List<Resource<?>>> bindings = new ArrayList<>();
        for (int index2 = 1; index2 < bindingArrayList.keyCount() + 1; index2++) {
            var bindingObject = bindingArrayList.get(index2);
            if (bindingObject instanceof LuaTable bindingList) {
                List<Resource<?>> bindingArray = new ArrayList<>();
                bindings.add(bindingArray);
                for (int index3 = 1; index3 < bindingList.keyCount() + 1; index3++) {
                    var binding = ((LuaJObj<Resource<?>>)bindingList.get(index3)).get();
                    bindingArray.add(binding);
                }
            } else if (bindingObject instanceof LuaJObj<?> bindingWrapper) {
                var binding = ((LuaJObj<Resource<?>>)bindingWrapper).get();
                bindings.add(List.of(binding));
            } else {
                throw new IllegalArgumentException("Unknown binding object " + bindingObject + " for set: " + index2);
            }
        }
        return bindings;
    }

    public LuaValue RaytracePass(LuaValue arg) {
        var pass = new TracePass(((LuaJObj<TracePipeline>)arg.get("pipeline")).get(), ()-> new Vector3i(ExecutionConstants.INSTANCE.getScreenSize(), 1));

        var bindingTable = arg.get("bindings").checktable();
        for (int index = 1; index < bindingTable.keyCount()+1; index++) {
            var bindingSetObject = bindingTable.get(index);
            if (bindingSetObject instanceof LuaJObj<?> lobj) {
                var obj = lobj.get();
                if (obj instanceof ExternalBoundLayout ebl) {
                    pass.bindLayout(index - 1, ebl);
                } else {
                    throw new IllegalArgumentException("Trying to bind unknown type " + obj + " to set: " + index);
                }
            } else {
                var bindings = generateBindingList(bindingSetObject.checktable());
                pass.bindLayout(index - 1, bindings.toArray(List[]::new));
            }
        }
        return new LuaJObj<>(pass);
    }

    public LuaValue ComputePass(LuaValue arg) {
        var pass = new ComputePass(((LuaJObj<ComputePipeline>)arg.get("pipeline")).get(), ()-> new Vector3i(ExecutionConstants.INSTANCE.getScreenSize(), 1));

        var bindingTable = arg.get("bindings").checktable();
        for (int index = 1; index < bindingTable.keyCount()+1; index++) {
            var bindingSetObject = bindingTable.get(index);
            if (bindingSetObject instanceof LuaJObj<?> lobj) {
                var obj = lobj.get();
                if (obj instanceof ExternalBoundLayout ebl) {
                    pass.bindLayout(index - 1, ebl);
                } else {
                    throw new IllegalArgumentException("Trying to bind unknown type " + obj + " to set: " + index);
                }
            } else {
                var bindings = generateBindingList(bindingSetObject.checktable());
                pass.bindLayout(index - 1, bindings.toArray(List[]::new));
            }
        }
        return new LuaJObj<>(pass);
    }

    public LuaValue setOutput(LuaValue arg) {
        var output = ((LuaJObj<Resource<?>>)arg.get(1)).get();
        this.graph = new RenderGraph(output);
        return LuaValue.NIL;
    }

    public void freeObjects() {
        for (var obj : this.allocatedObjects) {
            obj.free();
        }
    }
}
