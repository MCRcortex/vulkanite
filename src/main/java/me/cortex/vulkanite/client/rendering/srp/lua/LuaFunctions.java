package me.cortex.vulkanite.client.rendering.srp.lua;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.srp.api.execution.ExecutionConstants;
import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding;
import me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutCache;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.ComputePipeline;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.TracePipeline;
import me.cortex.vulkanite.client.rendering.srp.graph.RenderGraph;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline.ComputePass;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline.TracePass;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.BufferResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalBoundDescriptorSet;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ImageResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.Resource;
import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.pipeline.ComputePipelineBuilder;
import me.cortex.vulkanite.lib.pipeline.RaytracePipelineBuilder;
import me.cortex.vulkanite.lib.shader.ShaderModule;
import me.cortex.vulkanite.lib.shader.VShader;
import org.joml.Vector3i;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.lwjgl.system.MemoryUtil;

import java.util.*;

import static me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding.ACCESS_READ;
import static me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding.ACCESS_WRITE;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;

//TODO: create a set of vk resources that need to get freed
public class LuaFunctions {
    private final Set<TrackedResourceObject> allocatedObjects = new HashSet<>();

    private final VContext vctx = Vulkanite.INSTANCE.getCtx();
    private final LuaContextHost ctx;
    //TODO: make this layoutCache not in LuaFunctions
    private final LayoutCache layoutCache = new LayoutCache();

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

    public LuaValue Layout(LuaValue arg) {
        List<LayoutBinding> bindings = new ArrayList<>();
        var arr = arg.get("bindings").checktable();
        for (int i = 1; i < arr.keyCount() + 1; i++) {
            var bindingObj = arr.get(i).checktable();
            String name = null;
            if (bindingObj.get("name") != LuaValue.NIL) {
                name = bindingObj.get("name").checkjstring();
            }
            int bindingIndex = bindingObj.get("index").checkint();
            int type = bindingObj.get("type").checkint();
            int access = 0;
            if (bindingObj.get("access") == LuaValue.NIL) {
                access = switch (type) {
                    case VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER -> ACCESS_READ;
                    case VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE -> ACCESS_READ|ACCESS_WRITE;
                    default -> throw new IllegalStateException("Unknown description type: " + type);
                };
            } else{
                access = bindingObj.get("access").checkint();
            }
            int arrSize = 1;
            if (bindingObj.get("size") != LuaValue.NIL) {
                arrSize = bindingObj.get("size").checkint();
            }
            bindings.add(new LayoutBinding(name, bindingIndex, access, type, arrSize));

        }
        return new LuaJObj<>(new Layout(bindings));
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

    private static List<Layout> collectLayoutSets(LuaTable value) {
        List<Layout> sets = new ArrayList<>();
        for (int i = 1; i < value.keyCount() + 1; i++) {
            var setObj = value.get(i);
            if (setObj instanceof LuaJObj<?>) {
                var obj = ((LuaJObj<?>)setObj).get();
                if (obj instanceof ExternalBoundDescriptorSet externalLayout) {
                    sets.add(externalLayout.getLayout());
                } else if (obj instanceof Layout layout) {
                    sets.add(layout);
                } else {
                    throw new IllegalArgumentException("Unknown layout object: " + obj);
                }
            } else {
                throw new IllegalArgumentException("Unknown " + setObj);
            }
        }
        return sets;
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

        var sets = collectLayoutSets(arg.get("sets").checktable());
        //Convert the set layouts to physical set definitions and apply them to the builder
        sets.stream().map(layoutCache::getConcrete).forEach(builder::addLayout);
        var pipeline = builder.build(vctx, 1);
        this.allocatedObjects.add(pipeline);
        return new LuaJObj<>(new TracePipeline(pipeline, sets));
    }

    public LuaValue ComputePipeline(LuaValue arg) {
        var builder = new ComputePipelineBuilder();

        var shader = ((LuaJObj<VShader>)arg.get("shader")).get().named();
        builder.set(shader);

        var sets = collectLayoutSets(arg.get("sets").checktable());
        //Convert the set layouts to physical set definitions and apply them to the builder
        sets.stream().map(layoutCache::getConcrete).forEach(builder::addLayout);
        var pipeline = builder.build(vctx);
        this.allocatedObjects.add(pipeline);
        return new LuaJObj<>(new ComputePipeline(pipeline, sets));
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
        if (arg.get("name") != LuaValue.NIL) {
            pass.name(arg.get("name").checkjstring());
        }
        var bindingTable = arg.get("sets").checktable();
        for (int index = 1; index < bindingTable.keyCount()+1; index++) {
            var bindingSetObject = bindingTable.get(index);
            if (bindingSetObject instanceof LuaJObj<?> lobj) {
                var obj = lobj.get();
                if (obj instanceof ExternalBoundDescriptorSet ebl) {
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
        if (arg.get("name") != LuaValue.NIL) {
            pass.name(arg.get("name").checkjstring());
        }
        var bindingTable = arg.get("sets").checktable();
        for (int index = 1; index < bindingTable.keyCount()+1; index++) {
            var bindingSetObject = bindingTable.get(index);
            if (bindingSetObject instanceof LuaJObj<?> lobj) {
                var obj = lobj.get();
                if (obj instanceof ExternalBoundDescriptorSet ebl) {
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
        if (this.graph != null) {
            this.graph.destroy();
        }
        this.graph = new RenderGraph(this.layoutCache, output);
        return LuaValue.NIL;
    }

    public void freeObjects() {
        for (var obj : this.allocatedObjects) {
            obj.free();
        }
        this.layoutCache.freeAll();
    }
}
