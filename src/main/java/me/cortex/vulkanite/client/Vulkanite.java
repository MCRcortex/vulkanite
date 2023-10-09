package me.cortex.vulkanite.client;

import me.cortex.vulkanite.acceleration.AccelerationManager;
import me.cortex.vulkanite.acceleration.SharedQuadVkIndexBuffer;
import me.cortex.vulkanite.client.rendering.VulkanPipeline;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.initalizer.VInitializer;
import me.cortex.vulkanite.lib.descriptors.VDescriptorPool;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSetLayout;
import me.cortex.vulkanite.lib.descriptors.VTypedDescriptorPool;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.minecraft.util.Util;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.lwjgl.vulkan.EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME;
import static org.lwjgl.vulkan.EXTMemoryBudget.VK_EXT_MEMORY_BUDGET_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHR16bitStorage.VK_KHR_16BIT_STORAGE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHR8bitStorage.VK_KHR_8BIT_STORAGE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRDeferredHostOperations.VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalFenceCapabilities.VK_KHR_EXTERNAL_FENCE_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalFenceFd.VK_KHR_EXTERNAL_FENCE_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalFenceWin32.VK_KHR_EXTERNAL_FENCE_WIN32_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemory.VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryCapabilities.VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryFd.VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryWin32.VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphore.VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreCapabilities.VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreFd.VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreWin32.VK_KHR_EXTERNAL_SEMAPHORE_WIN32_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetMemoryRequirements2.VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRRayQuery.VK_KHR_RAY_QUERY_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRShaderDrawParameters.VK_KHR_SHADER_DRAW_PARAMETERS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSpirv14.VK_KHR_SPIRV_1_4_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;

public class Vulkanite {
    public static final boolean IS_WINDOWS = Util.getOperatingSystem() == Util.OperatingSystem.WINDOWS;

    public static boolean MEMORY_LEAK_TRACING = true;

    public static boolean IS_ENABLED = true;
    public static Vulkanite INSTANCE = new Vulkanite();

    private final VContext ctx;
    private final ArbitarySyncPointCallback fencedCallback = new ArbitarySyncPointCallback();

    private final AccelerationManager accelerationManager;
    private final HashMap<VDescriptorSetLayout, VTypedDescriptorPool> descriptorPools = new HashMap<>();

    public Vulkanite() {
        ctx = createVulkanContext();
        // Hack: so that AccelerationManager can access Vulkanite.INSTANCE
        INSTANCE = this;

        //Fill in the shared index buffer with a large count so we (hopefully) dont have to worry about it anymore
        // SharedQuadVkIndexBuffer.getIndexBuffer(ctx, 30000);

        accelerationManager = new AccelerationManager(ctx, 1);
    }

    public void upload(List<ChunkBuildOutput> results) {
        /*
        if (((IAccelerationBuildResult)result).getAccelerationGeometryData() == null)
            return;//TODO: delete the chunk section in this case then or something
        accelerationManager.chunkBuild(result);
         */

        accelerationManager.chunkBuilds(results);
    }

    public VTypedDescriptorPool getPoolByLayout(VDescriptorSetLayout layout) {
        synchronized (descriptorPools) {
            if (!descriptorPools.containsKey(layout)) {
                descriptorPools.put(layout, new VTypedDescriptorPool(ctx, layout, 0));
            }
            return descriptorPools.get(layout);
        }
    }

    public void removePoolByLayout(VDescriptorSetLayout layout) {
        synchronized (descriptorPools) {
            if (descriptorPools.containsKey(layout)) {
                descriptorPools.get(layout).free();
                descriptorPools.remove(layout);
            }
        }
    }

    public void sectionRemove(RenderSection section) {
        accelerationManager.sectionRemove(section);
    }

    public void renderTick() {
        ctx.sync.checkFences();
        accelerationManager.updateTick();
    }

    public void fenceTick() {
        fencedCallback.tick();
    }

    public VContext getCtx() {
        return ctx;
    }

    public void addSyncedCallback(Runnable callback) {
        fencedCallback.enqueue(callback);
    }

    public void destroy() {
        vkDeviceWaitIdle(ctx.device);
        for (var pool : descriptorPools.values()) {
            pool.free();
        }
        accelerationManager.cleanup();
    }

    private static VContext createVulkanContext() {
        var init = new VInitializer("Vulkan test", "Vulkanite", 1, 3,
                new String[]{VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_FENCE_CAPABILITIES_EXTENSION_NAME,
                        //VK_EXT_DEBUG_UTILS_EXTENSION_NAME
                },
                new String[] {
                        //"VK_LAYER_KHRONOS_validation",
                });

        //This copies whatever gpu the opengl context is on
        init.findPhysicalDevice();//glGetString(GL_RENDERER).split("/")[0]

        List<String> extensions = new ArrayList<>(List.of(
                VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME,
                VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME,
                VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME,
                VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME,
                VK_KHR_SPIRV_1_4_EXTENSION_NAME,
                VK_KHR_SHADER_DRAW_PARAMETERS_EXTENSION_NAME,

                //VK_KHR_RAY_QUERY_EXTENSION_NAME,

                VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME,
                VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME,

//                VK_EXT_MEMORY_BUDGET_EXTENSION_NAME,

                VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME
        ));
        if (IS_WINDOWS) {
            extensions.addAll(List.of(VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME,
                    VK_KHR_EXTERNAL_SEMAPHORE_WIN32_EXTENSION_NAME,
                    VK_KHR_EXTERNAL_FENCE_WIN32_EXTENSION_NAME));
        } else {
            extensions.addAll(List.of(VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME,
                    VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME,
                    VK_KHR_EXTERNAL_FENCE_FD_EXTENSION_NAME));
        }
        init.createDevice(extensions,
                List.of(),
                new float[]{1.0f, 0.9f},
                features -> features.shaderInt16(true).shaderInt64(true).multiDrawIndirect(true), List.of(
                        stack-> VkPhysicalDeviceAccelerationStructureFeaturesKHR.calloc(stack)
                                .sType$Default(),

                        stack-> VkPhysicalDeviceRayTracingPipelineFeaturesKHR.calloc(stack)
                                .sType$Default(),

                        stack-> VkPhysicalDeviceVulkan11Features.calloc(stack)
                                .sType$Default(),

                        stack-> VkPhysicalDeviceVulkan12Features.calloc(stack)
                                .sType$Default()
                ), List.of(
                        features-> {},
                        features -> {},
                        features -> {
                            var vulkan11Features = (VkPhysicalDeviceVulkan11Features) features;
                            vulkan11Features.protectedMemory(false);
                        },
                        features -> {
                            var vulkan12Features = (VkPhysicalDeviceVulkan12Features) features;
                            vulkan12Features.bufferDeviceAddressMultiDevice(false);
//                            vulkan12Features.bufferDeviceAddressCaptureReplay(false);
                        }
                ));

        return init.createContext();
    }

    public AccelerationManager getAccelerationManager() {
        return accelerationManager;
    }

}
