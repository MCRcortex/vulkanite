package me.cortex.vulkanite.lib.pipeline;

import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.shader.ShaderModule;
import org.lwjgl.vulkan.VkStridedDeviceAddressRegionKHR;

import java.util.Set;

import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.vkCmdTraceRaysKHR;
import static org.lwjgl.vulkan.VK10.*;

public class VRaytracePipeline extends TrackedResourceObject {
    private final VContext context;
    private final long pipeline;
    private final long layout;
    private final VBuffer shader_binding_table;
    private final VkStridedDeviceAddressRegionKHR gen;
    private final VkStridedDeviceAddressRegionKHR miss;
    private final VkStridedDeviceAddressRegionKHR hit;
    private final VkStridedDeviceAddressRegionKHR callable;
    private final Set<ShaderModule> shadersUsed;

    VRaytracePipeline(VContext context, long pipeline, long layout, VBuffer sbtMap,
                      VkStridedDeviceAddressRegionKHR raygen,
                      VkStridedDeviceAddressRegionKHR miss,
                      VkStridedDeviceAddressRegionKHR hit,
                      VkStridedDeviceAddressRegionKHR callable,
                      Set<ShaderModule> shadersUsed) {

        this.context = context;
        this.pipeline = pipeline;
        this.layout = layout;
        this.shader_binding_table = sbtMap;
        this.gen = raygen;
        this.miss = miss;
        this.hit = hit;
        this.callable = callable;
        this.shadersUsed = shadersUsed;
    }

    public void bind(VCmdBuff cmd) {
        vkCmdBindPipeline(cmd.buffer, VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR, pipeline);
    }

    public void trace(VCmdBuff cmd, int width, int height, int depth) {
        vkCmdTraceRaysKHR(cmd.buffer, gen, miss, hit, callable, width, height, depth);
    }

    public void bindDSet(VCmdBuff cmd, long... descs) {
        vkCmdBindDescriptorSets(cmd.buffer, VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR, layout, 0, descs, null);
    }

    public void free() {
        free0();
        vkDestroyPipeline(context.device, pipeline, null);
        shader_binding_table.free();
        gen.free();
        miss.free();
        hit.free();
        callable.free();
    }
}
