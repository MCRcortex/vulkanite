package me.cortex.vulkanite.client.rendering.srp.lua;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.TracePipeline;
import me.cortex.vulkanite.client.rendering.srp.graph.RenderGraph;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline.TracePass;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalBoundLayout;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.Resource;
import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.pipeline.RaytracePipelineBuilder;
import me.cortex.vulkanite.lib.shader.ShaderModule;
import me.cortex.vulkanite.lib.shader.VShader;
import me.cortex.vulkanite.lib.shader.reflection.ShaderReflection;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.*;

//TODO: create a set of vk resources that need to get freed
public class LuaFunctions {
    private final Set<TrackedResourceObject> allocatedObjects = new HashSet<>();

    private final VContext vctx = Vulkanite.INSTANCE.getCtx();
    private final LuaContextHost ctx;
    public LuaFunctions(LuaContextHost ctx) {
        this.ctx = ctx;
    }

    public LuaValue ShaderModule(LuaValue arg) {
        int stages = arg.get("type").checkint();
        if (arg.get("file") != LuaValue.NIL) {
            var source = ctx.getResource(arg.get("file").checkjstring());
            var shader = VShader.compileLoad(vctx, new String(source), stages);
            this.allocatedObjects.add(shader);
            return new LuaJObj<>(shader);
        } else {
            throw new IllegalArgumentException("Unknown loading method for args: " + arg);
        }
    }

    public LuaValue RaytracePipeline(LuaValue arg) {
        var builder = new RaytracePipelineBuilder();


        var rgen = ((LuaJObj<VShader>)arg.get("raygen")).get().named();
        builder.setRayGen(rgen);

        var missShaders = arg.get("raymiss").checktable();
        for (int index = 1; index < missShaders.keyCount()+1; index++) {
            var rmiss = ((LuaJObj<VShader>)missShaders.get(index)).get().named();
            builder.addMiss(rmiss);
        }

        var hitShaders = arg.get("rayhit").checktable();
        for (int index = 1; index < hitShaders.keyCount()+1; index++) {
            var hitEntry = hitShaders.get(index).checktable();
            ShaderModule chit = null;
            ShaderModule ahit = null;
            ShaderModule ihit = null;
            if (hitEntry.get("close") != LuaValue.NIL) {
                chit = ((LuaJObj<VShader>)hitEntry.get("close")).get().named();
            }
            if (hitEntry.get("any") != LuaValue.NIL) {
                ahit = ((LuaJObj<VShader>)hitEntry.get("any")).get().named();
            }
            if (hitEntry.get("intersect") != LuaValue.NIL) {
                ihit = ((LuaJObj<VShader>)hitEntry.get("intersect")).get().named();
            }
            builder.addHit(chit, ahit, ihit);
        }
        var pipeline = builder.build(vctx, 1);
        this.allocatedObjects.add(pipeline);
        return new LuaJObj<>(new TracePipeline(pipeline, pipeline.reflection.getSets().stream().map(LuaFunctions::buildLayout).toList()));
    }

    private static Layout buildLayout(ShaderReflection.Set set) {
        List<LayoutBinding> bindingList = new ArrayList<>();
        for (var binding : set.bindings()) {
            bindingList.add(new LayoutBinding(binding.name(), binding.binding(), 3, binding.descriptorType(), binding.runtimeSized()?-1:binding.arraySize()));
        }
        return new Layout(false, bindingList.toArray(LayoutBinding[]::new));
    }

    public LuaValue RaytracePass(LuaValue arg) {
        var pass = new TracePass(((LuaJObj<TracePipeline>)arg.get("pipeline")).get());

        var bindingTable = arg.get("bindings").checktable();
        for (int index = 1; index < bindingTable.keyCount()+1; index++) {
            var setBindingObject = bindingTable.get(index);
            if (setBindingObject instanceof LuaJObj<?> lobj) {
                var obj = lobj.get();
                if (obj instanceof ExternalBoundLayout ebl) {
                    pass.bindLayout(index - 1, ebl);
                } else {
                    throw new IllegalArgumentException("Trying to bind unknown type " + obj + " to set: " + index);
                }
            } else {
                List<List<Resource<?>>> bindings = new ArrayList<>();
                var setBindingsList = setBindingObject.checktable();
                for (int index2 = 1; index2 < setBindingsList.keyCount() + 1; index2++) {
                    var bindingObject = setBindingsList.get(index2);
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
                pass.bindLayout(index - 1, bindings.toArray(List[]::new));
            }
        }
        return new LuaJObj<>(pass);
    }

    public LuaValue setOutput(LuaValue arg) {
        var output = ((LuaJObj<Resource<?>>)arg.get(1)).get();
        var graph = new RenderGraph(output);
        return LuaValue.NIL;
    }

    public void freeObjects() {
        for (var obj : this.allocatedObjects) {
            obj.free();
        }
    }
}
