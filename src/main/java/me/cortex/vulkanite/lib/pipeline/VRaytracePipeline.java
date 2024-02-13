package me.cortex.vulkanite.lib.pipeline;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.shader.ShaderModule;
import me.cortex.vulkanite.lib.shader.reflection.ShaderReflection;
import org.lwjgl.vulkan.VkStridedDeviceAddressRegionKHR;

import java.util.Set;

import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.vkCmdTraceRaysKHR;
import static org.lwjgl.vulkan.VK10.*;

public class VRaytracePipeline extends VObject {
    private final VContext context;
    public final long pipeline;
    public final long layout;
    @SuppressWarnings("FieldCanBeLocal")
    private final VRef<VBuffer> shader_binding_table;
    public final VkStridedDeviceAddressRegionKHR gen;
    public final VkStridedDeviceAddressRegionKHR miss;
    public final VkStridedDeviceAddressRegionKHR hit;
    public final VkStridedDeviceAddressRegionKHR callable;
    private final Set<ShaderModule> shadersUsed;
    public final ShaderReflection reflection;

    VRaytracePipeline(VContext context, long pipeline, long layout, final VRef<VBuffer> sbtMap,
                      VkStridedDeviceAddressRegionKHR raygen,
                      VkStridedDeviceAddressRegionKHR miss,
                      VkStridedDeviceAddressRegionKHR hit,
                      VkStridedDeviceAddressRegionKHR callable,
                      Set<ShaderModule> shadersUsed,
                      ShaderReflection reflection) {
        this.context = context;
        this.pipeline = pipeline;
        this.layout = layout;
        this.shader_binding_table = sbtMap.addRef();
        this.gen = raygen;
        this.miss = miss;
        this.hit = hit;
        this.callable = callable;
        this.shadersUsed = shadersUsed;
        this.reflection = reflection;
    }

    protected void free() {
        vkDestroyPipeline(context.device, pipeline, null);
        gen.free();
        miss.free();
        hit.free();
        callable.free();
        reflection.freeLayouts();
    }
}
