package me.cortex.vulkanite.lib.shader.reflection;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.descriptors.DescriptorSetLayoutBuilder;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSetLayout;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.spvc.Spv.*;
import static org.lwjgl.util.spvc.Spvc.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.*;

public class ShaderReflection {
    public record Binding(String name, int binding, int descriptorType, int arraySize, boolean runtimeSized) {}
    public record Set(ArrayList<Binding> bindings) {
        public Set(ArrayList<Binding> bindings) {
            // Sort by binding
            this.bindings = bindings;
            this.bindings.sort((a, b) -> Integer.compare(a.binding, b.binding));
        }

        public Set(Binding ...bindings) {
            this(new ArrayList<Binding>(List.of(bindings)));
        }

        public Binding getBindingAt(int binding) {
            for (var b : bindings) {
                if (b.binding == binding) {
                    return b;
                }
            }
            return null;
        }

        public boolean hasUnsizedArrays() {
            for (var binding : bindings) {
                if (binding.runtimeSized) {
                    return true;
                }
            }
            return false;
        }

        public boolean validate(Set expected) {
            for (var binding : bindings) {
                var expectedBinding = expected.getBindingAt(binding.binding);
                if (expectedBinding == null) {
                    return false;
                }
                if (expectedBinding.descriptorType != binding.descriptorType) {
                    return false;
                }
                if (expectedBinding.runtimeSized != binding.runtimeSized) {
                    return false;
                }
                if (!expectedBinding.runtimeSized && expectedBinding.arraySize != binding.arraySize) {
                    return false;
                }
            }
            return true;
        }
    }
    private ArrayList<Set> sets = new ArrayList<>();

    public List<Binding> getBindings(int set) {
        return sets.get(set).bindings;
    }
    public Set getSet(int set) {
        return sets.get(set);
    }

    public List<Set> getSets() {
        return sets;
    }

    public int getNSets() {
        return sets.size();
    }

    public ShaderReflection() {
        //Empty
    }

    public ShaderReflection(ByteBuffer spirv) {
        try (var stack = stackPush()) {
            //Create context
            var ptr = stack.mallocPointer(1);
            var ptr2 = stack.mallocPointer(1);
            _CHECK_(spvc_context_create(ptr));
            long context = ptr.get(0);

            //Parse the spir-v
            _CHECK_(spvc_context_parse_spirv(context, spirv.asIntBuffer(), spirv.remaining()>>2, ptr));
            long ir = ptr.get(0);

            // Hand it off to a compiler instance and give it ownership of the IR.
            _CHECK_(spvc_context_create_compiler(context, SPVC_BACKEND_NONE, ir, SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, ptr));
            long compiler = ptr.get(0);

            // Create resources from spir-v
            _CHECK_(spvc_compiler_create_shader_resources(compiler, ptr));
            long resources = ptr.get(0);

            //Get reflection data
            for (var type : ResourceType.values()) {
                int vkDescType = type.toVkDescriptorType();

                _CHECK_(spvc_resources_get_resource_list_for_type(resources, type.id, ptr, ptr2));
                var reflectedResources = SpvcReflectedResource.create(ptr.get(0), (int) ptr2.get(0));
                for (var reflect : reflectedResources) {
                    if (vkDescType != -1) {
                        int binding = spvc_compiler_get_decoration(compiler, reflect.id(), SpvDecorationBinding);
                        int set = spvc_compiler_get_decoration(compiler, reflect.id(), SpvDecorationDescriptorSet);
                        var spvcType = spvc_compiler_get_type_handle(compiler, reflect.type_id());
                        //System.err.println("\n");
                        //System.err.println(spvc_type_get_image_access_qualifier(spvcType));
                        //System.err.println(spvc_compiler_get_name(compiler, reflect.id()));
                        //System.err.println(reflect.nameString());
                        //System.err.println(spvc_compiler_has_decoration(compiler, reflect.id(), SpvDecorationRestrict));
                        //System.err.println(spvc_compiler_has_decoration(compiler, reflect.id(), SpvDecorationNonReadable));
                        //System.err.println(spvc_compiler_has_decoration(compiler, reflect.id(), SpvDecorationNonWritable));
                        int arrayNDims = spvc_type_get_num_array_dimensions(spvcType);
                        int arraySize = 0;
                        String name = spvc_compiler_get_name(compiler, reflect.id());
                        if (arrayNDims > 0) {
                            arraySize = 1;
                            for (int i = 0; i < arrayNDims; i++) {
                                arraySize *= spvc_type_get_array_dimension(spvcType, i);
                            }
                        }
                        boolean isRuntimeSized = false;
                        if (arraySize == 0 && arrayNDims > 0) {
                            isRuntimeSized = true;
                            arraySize = 1;
                        }
                        var descriptor = new Binding(name, binding, vkDescType, arraySize, isRuntimeSized);
                        while (sets.size() <= set) {
                            sets.add(new Set(new ArrayList<>()));
                        }
                        sets.get(set).bindings.add(descriptor);
                    }
                }
            }

            spvc_context_destroy(context);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int set = 0; set < sets.size(); set++) {
            sb.append("Set ").append(set).append(":\n");
            for (var binding : sets.get(set).bindings) {
                sb.append("  - ").append(binding.binding).append(" : ").append(binding.name).append("; arraySize = ").append(binding.arraySize).append("; runtimeSized = ").append(binding.runtimeSized).append("\n");
            }
        }
        return sb.toString();
    }

    public static ShaderReflection mergeStages(ShaderReflection ...stages) {
        ShaderReflection out = new ShaderReflection();
        int maxSets = 0;
        for (var stage : stages) {
            maxSets = Math.max(maxSets, stage.getNSets());
        }
        for (int set = 0; set < maxSets; set++) {
            var bindings = new ArrayList<Binding>();
            for (var stage : stages) {
                if (stage.getNSets() > set) {
                    var stageBindings = stage.getBindings(set);
                    for (var binding : stageBindings) {
                        boolean alreadyExists = false;
                        for (var b : bindings) {
                            if (b.binding == binding.binding) {
                                // Check for conflicts
                                if (b.descriptorType != binding.descriptorType) {
                                    throw new IllegalStateException("Conflicting descriptor types for binding " + binding.binding + " in set " + set);
                                }
                                if (b.runtimeSized != binding.runtimeSized) {
                                    throw new IllegalStateException("Conflicting runtime sized for binding " + binding.binding + " in set " + set);
                                }
                                if (!b.runtimeSized && b.arraySize != binding.arraySize) {
                                    throw new IllegalStateException("Conflicting array sizes for binding " + binding.binding + " in set " + set);
                                }
                                // We don't check for name conflicts, but still warn
                                if (!b.name.isEmpty() && !binding.name.isEmpty() && b.name.compareTo(binding.name) != 0) {
                                    System.err.println("Warning: Conflicting names for binding " + binding.binding + " : " + b.name + " and " + binding.name);
                                }
                                alreadyExists = true;
                            }
                        }
                        if (!alreadyExists) {
                            bindings.add(binding);
                        }
                    }
                }
            }
            out.sets.add(new Set(bindings));
        }
        return out;
    }

    public List<VDescriptorSetLayout> buildSetLayouts(VContext context) {
        // TODO: Pick a better number, somehow
        return buildSetLayouts(context, 65536);
    }

    private List<VDescriptorSetLayout> layouts = new ArrayList<>();
    public List<VDescriptorSetLayout> buildSetLayouts(VContext context, int runtimeSizedArrayMaxSize) {
        freeLayouts();
        layouts = new ArrayList<>();
        for (var set : sets) {
            int flags = 0;
            if (set.hasUnsizedArrays()) {
                flags |= VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT;
            }
            var builder = new DescriptorSetLayoutBuilder(flags);
            for (var binding : set.bindings) {
                if (binding.arraySize > 0) {
                    if (binding.runtimeSized) {
                        builder.binding(binding.binding, binding.descriptorType, runtimeSizedArrayMaxSize, VK_SHADER_STAGE_ALL);
                        builder.setBindingFlags(binding.binding,
                                VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT
                                        | VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT
                                        | VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT);
                    } else {
                        builder.binding(binding.binding, binding.descriptorType, binding.arraySize, VK_SHADER_STAGE_ALL);
                        builder.setBindingFlags(binding.binding, VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT);
                    }
                } else {
                    builder.binding(binding.binding, binding.descriptorType, VK_SHADER_STAGE_ALL);
                }
            }
            layouts.add(builder.build(context));
        }
        return layouts;
    }

    public final List<VDescriptorSetLayout> getLayouts() {
        return layouts;
    }

    public void freeLayouts() {
        for (var l : layouts) {
            l.free();
        }
    }

    private static void _CHECK_(int status) {
        if (status != 0) {
            throw new IllegalStateException("Got status: " + status);
        }
    }
}
