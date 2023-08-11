package me.cortex.vulkanite.lib.pipeline;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.sync.VFence;
import org.lwjgl.vulkan.VkStridedDeviceAddressRegionKHR;

import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.vkCmdTraceRaysKHR;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;

public class VRaytracePipeline {
    private final VContext context;
    private final long pipeline;
    private final long layout;
    private final VBuffer stbMap;
    private final VkStridedDeviceAddressRegionKHR gen;
    private final VkStridedDeviceAddressRegionKHR miss;
    private final VkStridedDeviceAddressRegionKHR hit;
    private final VkStridedDeviceAddressRegionKHR callable;
    VRaytracePipeline(VContext context, long pipeline, long layout, VBuffer sbtMap,
                      VkStridedDeviceAddressRegionKHR raygen,
                      VkStridedDeviceAddressRegionKHR miss,
                      VkStridedDeviceAddressRegionKHR hit,
                      VkStridedDeviceAddressRegionKHR callable) {
        this.context = context;
        this.pipeline = pipeline;
        this.layout = layout;
        this.stbMap = sbtMap;
        this.gen = raygen;
        this.miss = miss;
        this.hit = hit;
        this.callable = callable;
    }

    public void bind(VCmdBuff cmd) {
        vkCmdBindPipeline(cmd.buffer, VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR, pipeline);
    }

    public void trace(VCmdBuff cmd, int width, int height, int depth) {
        vkCmdTraceRaysKHR(cmd.buffer, gen, miss, hit, callable, width, height, depth);
    }

    public void free(VFence fence) {

    }
}
