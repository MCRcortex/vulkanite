package me.cortex.vulkanite.lib.pipeline;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSetLayout;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.shader.ShaderModule;
import me.cortex.vulkanite.lib.shader.reflection.ShaderReflection;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.*;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static me.cortex.vulkanite.lib.other.VUtil.alignUp;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;
import static org.lwjgl.vulkan.VK10.*;

public class RaytracePipelineBuilder {

    private final Set<ShaderModule> shaders = new LinkedHashSet<>();
    public RaytracePipelineBuilder() {

    }

    private ShaderModule gen;
    public RaytracePipelineBuilder setRayGen(ShaderModule gen) {
        this.gen = gen;
        return this;
    }

    private final List<ShaderModule> missGroups = new ArrayList<>();
    public RaytracePipelineBuilder addMiss(ShaderModule miss) {
        shaders.add(miss);
        missGroups.add(miss);
        return this;
    }

    private record HitG(ShaderModule chit, ShaderModule ahit, ShaderModule intr) {}
    private final List<HitG> hitGroups = new ArrayList<>();
    public RaytracePipelineBuilder addHit(ShaderModule chit, ShaderModule ahit, ShaderModule intr) {
        if (chit != null) shaders.add(chit);
        if (ahit != null) shaders.add(ahit);
        if (intr != null) shaders.add(intr);
        hitGroups.add(new HitG(chit, ahit, intr));
        return this;
    }

    private final List<ShaderModule> callGroups = new ArrayList<>();
    public RaytracePipelineBuilder addCallable(ShaderModule callable) {
        shaders.add(callable);
        callGroups.add(callable);
        return this;
    }

    ArrayList<VDescriptorSetLayout> layouts = new ArrayList<>();
    public RaytracePipelineBuilder addLayout(VDescriptorSetLayout layout) {
        layouts.add(layout);
        return this;
    }

    //TODO: generate stb
    public VRaytracePipeline build(VContext context, int maxDepth) {
        shaders.add(gen);

        ArrayList<ShaderReflection> reflections = new ArrayList<>();
        ShaderReflection reflection = null;
        for (var shader : shaders) {
            reflections.add(shader.shader().getReflection());
        }
        try {
            reflection = ShaderReflection.mergeStages(reflections.toArray(ShaderReflection[]::new));
            System.out.println("Raytracing pipeline reflection:\n" + reflection);
        } catch (Exception e) {
            System.out.println("Failed to merge shader reflections, this is likely due to a mismatch in descriptor sets");
            throw e;
        }

        if (layouts.isEmpty()) {
            layouts.addAll(reflection.buildSetLayouts(context));
        }

        try (var stack = stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(shaders.size(), stack);
            Map<ShaderModule, Integer> shader2id = new HashMap<>();
            {
                for (var shader : shaders) {
                    var struct = shaderStages.get(shader2id.size());
                    shader2id.put(shader, shader2id.size());
                    shader.setupStruct(stack, struct);
                }
            }

            VkRayTracingShaderGroupCreateInfoKHR.Buffer groupsArr = VkRayTracingShaderGroupCreateInfoKHR
                    .calloc(1 + missGroups.size() + hitGroups.size() + callGroups.size(), stack);
            groupsArr.forEach(a->a.sType$Default()
                    .generalShader(VK_SHADER_UNUSED_KHR)
                    .intersectionShader(VK_SHADER_UNUSED_KHR)
                    .closestHitShader(VK_SHADER_UNUSED_KHR)
                    .anyHitShader(VK_SHADER_UNUSED_KHR)
            );

            {
                //Set ray gen shader
                groupsArr.get()
                        .type(VK_SHADER_GROUP_SHADER_GENERAL_KHR)
                        .generalShader(shader2id.get(gen));

                //Set the miss groups
                missGroups.forEach(shader->{
                    groupsArr.get()
                            .type(VK_SHADER_GROUP_SHADER_GENERAL_KHR)
                            .generalShader(shader2id.get(shader));
                });

                //Set the hit groups shader
                hitGroups.forEach(hit->{
                    groupsArr.get()
                            .type(hit.intr==null?VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_KHR:VK_RAY_TRACING_SHADER_GROUP_TYPE_PROCEDURAL_HIT_GROUP_KHR)
                            .closestHitShader(hit.chit==null?VK_SHADER_UNUSED_KHR:shader2id.get(hit.chit))
                            .anyHitShader(hit.ahit==null?VK_SHADER_UNUSED_KHR:shader2id.get(hit.ahit))
                            .intersectionShader(hit.intr==null?VK_SHADER_UNUSED_KHR:shader2id.get(hit.intr))
                    ;
                });

                groupsArr.rewind();
            }

            VkPipelineLayoutCreateInfo layoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default();

            {
                //TODO: cleanup and add push constants
                layoutCreateInfo.pSetLayouts(stack.longs(layouts.stream().mapToLong(a->a.layout).toArray()));
            }

            LongBuffer pLayout = stack.mallocLong(1);
            _CHECK_(vkCreatePipelineLayout(context.device, layoutCreateInfo, null, pLayout));


            var pipelineCreateInfo = VkRayTracingPipelineCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .layout(pLayout.get(0))
                    .pStages(shaderStages)
                    .pGroups(groupsArr)
                    .maxPipelineRayRecursionDepth(maxDepth);

            LongBuffer pPipeline = stack.mallocLong(1);
            _CHECK_(vkCreateRayTracingPipelinesKHR(context.device, VK_NULL_HANDLE, VK_NULL_HANDLE,
                    VkRayTracingPipelineCreateInfoKHR.create(pipelineCreateInfo.address(), 1),
                    null, pPipeline));


            {
                //Generate the SBT lut
                var props = context.properties.rtPipelineProperties;
                long groupBaseAlignment = props.shaderGroupBaseAlignment();
                long handleSize = props.shaderGroupHandleSize();
                long handleSizeAligned = alignUp(handleSize, props.shaderGroupHandleAlignment());

                int totalGroups = groupsArr.capacity();

                long rgenBase = 0;
                long missGroupBase = alignUp(rgenBase + handleSizeAligned, groupBaseAlignment);
                long missGroupCount = missGroups.size();
                long hitGroupsBase = alignUp(missGroupBase + handleSizeAligned * missGroupCount, groupBaseAlignment);
                long hitGroupsCount = hitGroups.size();
                long callGroupBase = alignUp(hitGroupsBase + handleSizeAligned * hitGroupsCount, groupBaseAlignment);
                long callGroupCount = callGroups.size();

                // Pad an extra handle size at the end, so that even if we don't have call groups, the address is still in bounds
                long sbtSize = alignUp(callGroupBase + callGroupCount * handleSizeAligned + handleSizeAligned, groupBaseAlignment);

                ByteBuffer handles = stack.malloc(totalGroups * (int)handleSize);
                _CHECK_(vkGetRayTracingShaderGroupHandlesKHR(context.device, pPipeline.get(0), 0, totalGroups, handles),
                        "Failed to obtain ray tracing group handles");

                long aHandles = MemoryUtil.memAddress(handles);

                //TODO/FIXME: add alignment to gpu buffer
                VBuffer sbtMap = context.memory.createBuffer(sbtSize,
                        VK_BUFFER_USAGE_TRANSFER_DST_BIT |
                                VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR |
                                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR,
                        VK_MEMORY_HEAP_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                        0, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
                sbtMap.setDebugUtilsObjectName("SBT");
                long ptr = sbtMap.map();

                // Groups in order of RayGen, Miss Groups, Hit Groups, and callable
                // 1. Copy ray gen
                memCopy(aHandles, ptr + rgenBase, handleSize);
                aHandles += handleSize;
                // 2. Copy miss groups
                for (int i = 0; i < missGroupCount; i++) {
                    memCopy(aHandles, ptr + missGroupBase + handleSizeAligned * i, handleSize);
                    aHandles += handleSize;
                }
                // 3. Copy hit groups
                for (int i = 0; i < hitGroupsCount; i++) {
                    memCopy(aHandles, ptr + hitGroupsBase + handleSizeAligned * i, handleSize);
                    aHandles += handleSize;
                }
                // 4. Copy callable
                for (int i = 0; i < callGroupCount; i++) {
                    memCopy(aHandles, ptr + callGroupBase + handleSizeAligned * i, handleSize);
                    aHandles += handleSize;
                }

                sbtMap.unmap();
                sbtMap.flush();

                return new VRaytracePipeline(context, pPipeline.get(0), pLayout.get(0), sbtMap,
                        VkStridedDeviceAddressRegionKHR.calloc().set(sbtMap.deviceAddress() + rgenBase, handleSizeAligned, handleSizeAligned),
                        VkStridedDeviceAddressRegionKHR.calloc().set(sbtMap.deviceAddress() + missGroupBase, handleSizeAligned, handleSizeAligned * missGroupCount),
                        VkStridedDeviceAddressRegionKHR.calloc().set(sbtMap.deviceAddress() + hitGroupsBase, handleSizeAligned, handleSizeAligned * hitGroupsCount),
                        VkStridedDeviceAddressRegionKHR.calloc().set(sbtMap.deviceAddress() + callGroupBase, handleSizeAligned, handleSizeAligned * callGroupCount),
                        shaders,
                        reflection
                );
            }
        }
    }
}
