package me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline;

import java.util.*;
import java.util.function.Function;

import me.cortex.vulkanite.client.rendering.srp.api.VirtualResourceMapper;
import me.cortex.vulkanite.client.rendering.srp.api.execution.DescriptorSetBuilder;
import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.api.pipeline.Pipeline;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.Pass;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.*;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;

import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.VK10.*;

public abstract class PipelinePass<T extends PipelinePass<T, J, P>, J extends Pipeline<J, P>, P> extends Pass<T> {
    protected final Pipeline<J, P> pipeline;
    protected final Map<Layout, Object> layoutBindings = new HashMap<>();

    private final List<Function<VCmdBuff, Long>> descriptorSetProviders = new ArrayList<>();

    public PipelinePass(Pipeline<J, P> pipeline) {
        this.pipeline = pipeline;
    }

    public T bindLayout(Layout layout, Resource<?>... bindings) {
        return (T) this.bindLayout(layout, Arrays.stream(bindings).map(List::of).toArray(List[]::new));
    }

    public T bindLayout(Layout layout, List<Resource<?>>... bindingArray) {
        List<List<Resource<?>>> bindings = List.of(bindingArray);
        if (this.layoutBindings.containsKey(layout)) {
            throw new IllegalStateException("Already bound layout");
        }
        var bindingPoints = layout.getBindings();
        if (bindingPoints.size() != bindings.size()) {
            throw new IllegalStateException("Incorrect number of binding points");
        }

        for (int i = 0; i < bindingPoints.size(); i++) {
            var bindingPoint = bindingPoints.get(i);
            var bindingList = bindings.get(i);

            if (bindingPoint.reads()) {
                for (var binding : bindingList) {
                    this.reads(binding);
                }
            }

            if (bindingPoint.writes()) {
                for (var binding : bindingList) {
                    this.writes(binding);
                }
            }
        }

        this.layoutBindings.put(layout, bindings);
        return (T)this;
    }

    public T bindLayout(Layout layout, ExternalBoundDescriptorSet binding) {
        if (this.layoutBindings.containsKey(layout)) {
            throw new IllegalStateException("Already bound layout");
        }
        this.layoutBindings.put(layout, binding);
        return (T)this;
    }

    public T bindLayout(int index, Resource<?>... bindings) {
        return this.bindLayout(this.pipeline.getLayoutSet(index), bindings);
    }

    public T bindLayout(int index, ExternalBoundDescriptorSet binding) {
        return this.bindLayout(this.pipeline.getLayoutSet(index), binding);
    }

    public T bindLayout(int index, List<Resource<?>>... bindings) {
        return this.bindLayout(this.pipeline.getLayoutSet(index), bindings);
    }

    public T bindLayout(Resource<?>... bindings) {
        return this.bindLayout(0, bindings);
    }

    @Override
    public void verifyAndPrep(VirtualResourceMapper resourceMapper) {
        super.verifyAndPrep(resourceMapper);
        this.validateLayoutBindings(resourceMapper);
    }

    protected void validateLayoutBindings(VirtualResourceMapper resourceMapper) {
        //Verify that the all the layout exist
        if (!(this.layoutBindings.keySet().containsAll(this.pipeline.getLayouts()) &&
                new HashSet<>(this.pipeline.getLayouts()).containsAll(this.layoutBindings.keySet()))) {
            throw new IllegalStateException("Pipeline doesnt have all the layouts bound");
        }

        for (var layout : this.pipeline.getLayouts()) {
            var bindingObject = this.layoutBindings.get(layout);
            if (bindingObject instanceof ExternalBoundDescriptorSet boundLayout) {
                if (!boundLayout.layout().equals(layout)) {
                    throw new IllegalStateException("External bound layout does not match expected layout");
                }
                this.descriptorSetProviders.add((cmd)->boundLayout.getConcrete());
            } else if (bindingObject instanceof List<?> bindingList) {
                var bindings = (List<List<Resource<?>>>)bindingList;
                if (bindings.size() != layout.getBindings().size()) {
                    throw new IllegalStateException("Binding length does not match");
                }
                var bindingPoints = layout.getBindings();
                for (int i = 0; i < bindings.size(); i++) {
                    var binding = bindings.get(i);
                    var point = bindingPoints.get(i);
                    int pointType = switch (point.type()) {
                        case VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER -> 0;
                        case VK_DESCRIPTOR_TYPE_STORAGE_BUFFER -> 0;
                        case VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER -> 1;
                        case VK_DESCRIPTOR_TYPE_STORAGE_IMAGE -> 1;
                        case VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR -> 3;

                        default -> throw new IllegalArgumentException("Unknown type: " + point.type());
                    };
                    int bindingType = -1;
                    var test = binding.get(0);
                    if (test instanceof BufferResource) {
                        bindingType = 0;
                    } else if (test instanceof ImageResource) {
                        bindingType = 1;
                    } else if (test instanceof ExternalAccelerationResource) {
                        bindingType = 3;
                    } else {
                        throw new IllegalStateException("Unknown binding type: " + test);
                    }

                    if (pointType != bindingType) {
                        throw new IllegalStateException("Binding type and point type are not the same");
                    }
                }
                var descriptorSet = DescriptorSetBuilder.createDescriptorSet(resourceMapper, layout, bindings);
                this.descriptorSetProviders.add(descriptorSet::updateAndGetSet);
            } else {
                throw new IllegalStateException("Unknown binding method " + bindingObject);
            }
        }
    }

    protected long[] getDescriptorSets(VCmdBuff cmd) {
        var sets = new long[this.descriptorSetProviders.size()];
        for (int i = 0; i < sets.length; i++) {
            sets[i] = this.descriptorSetProviders.get(i).apply(cmd);
        }
        return sets;
    }
}
