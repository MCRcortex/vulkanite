package me.cortex.vulkanite.client;

import me.cortex.vulkanite.acceleration.AccelerationManager;
import me.cortex.vulkanite.acceleration.SharedQuadVkIndexBuffer;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.initalizer.VInitializer;
import me.cortex.vulkanite.mixin.MixinRenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import org.lwjgl.vulkan.VkPhysicalDeviceAccelerationStructureFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceBufferDeviceAddressFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceRayQueryFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceRayTracingPipelineFeaturesKHR;

import java.util.List;

import static org.lwjgl.vulkan.EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRDeferredHostOperations.VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalFenceCapabilities.VK_KHR_EXTERNAL_FENCE_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalFenceWin32.VK_KHR_EXTERNAL_FENCE_WIN32_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemory.VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryCapabilities.VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryWin32.VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphore.VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreCapabilities.VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreWin32.VK_KHR_EXTERNAL_SEMAPHORE_WIN32_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetMemoryRequirements2.VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRRayQuery.VK_KHR_RAY_QUERY_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRShaderDrawParameters.VK_KHR_SHADER_DRAW_PARAMETERS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSpirv14.VK_KHR_SPIRV_1_4_EXTENSION_NAME;

public class Vulkanite {
    public static boolean IS_ENABLED = true;
    public static final Vulkanite INSTANCE = new Vulkanite();

    private final VContext ctx;

    private AccelerationManager accelerationManager;

    public Vulkanite() {
        ctx = createVulkanContext();

        //Fill in the shared index buffer with a large count so we (hopefully) dont have to worry about it anymore
        SharedQuadVkIndexBuffer.getIndexBuffer(ctx, 10000);

        accelerationManager = new AccelerationManager(ctx, 1);
    }

    public void upload(List<ChunkBuildResult> results) {
        /*
        if (((IAccelerationBuildResult)result).getAccelerationGeometryData() == null)
            return;//TODO: delete the chunk section in this case then or something
        accelerationManager.chunkBuild(result);
         */
        accelerationManager.chunkBuilds(results);
    }

    public void sectionRemove(RenderSection section) {
        accelerationManager.sectionRemove(section);
    }

    public void renderTick() {
        ctx.sync.checkFences();

        //TODO: move this to final position (as early as possible before the actual ray rendering to give it time to build (doesnt need to be gl synced))
        accelerationManager.updateTick();
    }

    private static VContext createVulkanContext() {
        var init = new VInitializer("Vulkan test", "Vulkanite", 1, 3,
                new String[]{VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_FENCE_CAPABILITIES_EXTENSION_NAME,
                        VK_EXT_DEBUG_UTILS_EXTENSION_NAME
                },
                new String[] {
                        "VK_LAYER_KHRONOS_validation",
                });

        //This copies whatever gpu the opengl context is on
        init.findPhysicalDevice();//glGetString(GL_RENDERER).split("/")[0]

        init.createDevice(List.of(
                        VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_SEMAPHORE_WIN32_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_FENCE_WIN32_EXTENSION_NAME,
                        VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME,
                        VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME,
                        VK_KHR_SPIRV_1_4_EXTENSION_NAME,
                        VK_KHR_SHADER_DRAW_PARAMETERS_EXTENSION_NAME,
                        VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME,

                        VK_KHR_RAY_QUERY_EXTENSION_NAME,

                        VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME,
                        VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME,

                        VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME),
                List.of(),
                new float[]{1.0f, 0.9f},
                features -> features.shaderInt64(true).multiDrawIndirect(true), List.of(
                        stack-> VkPhysicalDeviceBufferDeviceAddressFeaturesKHR.calloc(stack)
                                .sType$Default()
                                .bufferDeviceAddress(true),

                        stack-> VkPhysicalDeviceAccelerationStructureFeaturesKHR.calloc(stack)
                                .sType$Default()
                                .accelerationStructure(true),

                        stack-> VkPhysicalDeviceRayQueryFeaturesKHR.calloc(stack)
                                .sType$Default()
                                .rayQuery(true),

                        stack-> VkPhysicalDeviceRayTracingPipelineFeaturesKHR.calloc(stack)
                                .sType$Default()
                                .rayTracingPipeline(true)
                                .rayTracingPipelineTraceRaysIndirect(true)
                ));

        return init.createContext();
    }

}
