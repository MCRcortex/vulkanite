package me.cortex.vulkanite.lib.cmd;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSet;
import me.cortex.vulkanite.lib.memory.MemoryManager;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.memory.VImage;

import me.cortex.vulkanite.lib.other.VQueryPool;
import me.cortex.vulkanite.lib.other.sync.VGSemaphore;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.cortex.vulkanite.lib.pipeline.VComputePipeline;
import me.cortex.vulkanite.lib.pipeline.VRaytracePipeline;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.*;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR;

import java.util.*;

//TODO: Track with TrackedResourceObject but need to be careful due to how the freeing works
public class VCmdBuff extends VObject {
    private final VCommandPool pool;
    private VkCommandBuffer buffer;

    private final List<VRef<VQueryPool>> queryPoolRefs = new ArrayList<>();
    private final List<VRef<VObject>> refs = new ArrayList<>();

    public void addBufferRef(final VRef<VBuffer> buffer) {
        refs.add(buffer.addRefGeneric());
    }
    public void addImageRef(final VRef<VImage> image) {
        refs.add(image.addRefGeneric());
    }
    public void addSemaphoreRef(final VRef<VSemaphore> semaphore) {
        refs.add(semaphore.addRefGeneric());
    }
    public void addVGSemaphoreRef(final VRef<VGSemaphore> semaphore) {
        refs.add(semaphore.addRefGeneric());
    }

    protected VCmdBuff(VCommandPool pool, VkCommandBuffer buff, int flags) {
        this.pool = pool;
        this.buffer = buff;

        try (var stack = stackPush()) {
            vkBeginCommandBuffer(buffer, VkCommandBufferBeginInfo.calloc(stack).sType$Default().flags(flags));
        }
    }

    public final VkCommandBuffer buffer() {
        return buffer;
    }

    private VkCommandBuffer finalizedBuffer = null;

    public VkCommandBuffer seal() {
        if (finalizedBuffer != null) {
            return finalizedBuffer;
        }
        finalizedBuffer = buffer;
        buffer = null;
        vkEndCommandBuffer(finalizedBuffer);
        return finalizedBuffer;
    }

    private long currentPipelineLayout = -1;
    private int currentShaderStageMask = 0;
    private int currentPipelineBindPoint = -1;

    private VkStridedDeviceAddressRegionKHR gen, miss, hit, callable;

    public void bindCompute(final VRef<VComputePipeline> pipeline) {
        vkCmdBindPipeline(buffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.get().pipeline());
        refs.add(new VRef<>(pipeline.get()));
        currentPipelineLayout = pipeline.get().layout();
        currentShaderStageMask = VK_SHADER_STAGE_COMPUTE_BIT;
        currentPipelineBindPoint = VK_PIPELINE_BIND_POINT_COMPUTE;
    }

    public void bindRT(final VRef<VRaytracePipeline> pipeline) {
        vkCmdBindPipeline(buffer, VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR, pipeline.get().pipeline);
        refs.add(new VRef<>(pipeline.get()));
        currentPipelineLayout = pipeline.get().layout;
        currentShaderStageMask = VK_SHADER_STAGE_RAYGEN_BIT_KHR | VK_SHADER_STAGE_MISS_BIT_KHR | VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR | VK_SHADER_STAGE_CALLABLE_BIT_KHR;
        currentPipelineBindPoint = VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR;

        gen = pipeline.get().gen;
        miss = pipeline.get().miss;
        hit = pipeline.get().hit;
        callable = pipeline.get().callable;
    }

    public void traceRays(int width, int height, int depth) {
        vkCmdTraceRaysKHR(buffer, gen, miss, hit, callable, width, height, depth);
    }

    public void bindDSet(VRef<VDescriptorSet>... sets) {
        long[] vkSets = Arrays.stream(sets).mapToLong(s -> s.get().set).toArray();
        vkCmdBindDescriptorSets(buffer, currentPipelineBindPoint, currentPipelineLayout, 0, vkSets, null);
        refs.addAll(Arrays.stream(sets).map(s -> new VRef<VObject>(s.get())).toList());
    }

    public void bindDSet(List<VRef<VDescriptorSet>> sets) {
        long[] vkSets = sets.stream().mapToLong(s -> s.get().set).toArray();
        vkCmdBindDescriptorSets(buffer, currentPipelineBindPoint, currentPipelineLayout, 0, vkSets, null);
        refs.addAll(sets.stream().map(s -> new VRef<VObject>(s.get())).toList());
    }

    public void pushConstants(int offset, int size, long dataPtr) {
        nvkCmdPushConstants(buffer, currentPipelineLayout, VK_SHADER_STAGE_ALL, offset, size, dataPtr);
    }

    public void pushConstants(int offset, long[] data) {
        vkCmdPushConstants(buffer, currentPipelineLayout, VK_SHADER_STAGE_ALL, offset, data);
    }

    public void dispatch(int x, int y, int z) {
        if (currentShaderStageMask != VK_SHADER_STAGE_COMPUTE_BIT || currentPipelineLayout == -1) {
            throw new IllegalStateException("No compute pipeline bound");
        }
        vkCmdDispatch(buffer, x, y, z);
    }

    public void resetQueryPool(final VRef<VQueryPool> queryPool, int first, int size) {
        vkCmdResetQueryPool(buffer, queryPool.get().pool, first, size);
        queryPoolRefs.add(queryPool.addRef());
    }

    public void encodeDataUpload(MemoryManager manager, long src, final VRef<VBuffer> dest, long destOffset, long size) {
        VRef<VBuffer> staging = manager.createBuffer(size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, 0,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
        staging.get().setDebugUtilsObjectName("Data Upload Host Staging");
        long ptr = staging.get().map();
        MemoryUtil.memCopy(src, ptr, size);
        staging.get().unmap();

        try (var stack = stackPush()) {
            var copy = VkBufferCopy.calloc(1, stack);
            copy.get(0).srcOffset(0).dstOffset(destOffset).size(size);
            vkCmdCopyBuffer(buffer, staging.get().buffer(), dest.get().buffer(), copy);
        }

        addBufferRef(staging);
        addBufferRef(dest);
    }

    public void encodeImageUpload(MemoryManager manager, long src, final VRef<VImage> dest, long srcSize, int destLayout) {
        VRef<VBuffer> staging = manager.createBuffer(srcSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, 0,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
        staging.get().setDebugUtilsObjectName("Image Upload Host Staging");
        long ptr = staging.get().map();
        MemoryUtil.memCopy(src, ptr, srcSize);
        staging.get().unmap();

        try (var stack = stackPush()) {
            var copy = VkBufferImageCopy.calloc(1, stack);
            copy.get(0).bufferOffset(0).bufferImageHeight(0).bufferRowLength(0)
                    .imageOffset(o -> o.set(0, 0, 0))
                    .imageExtent(extent -> extent.set(dest.get().width, dest.get().height, dest.get().depth))
                    .imageSubresource(s -> s.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseArrayLayer(0).layerCount(1).mipLevel(0));
            vkCmdCopyBufferToImage(buffer, staging.get().buffer(), dest.get().image(), destLayout, copy);
        }

        addBufferRef(staging);
        addImageRef(dest);
    }

    public void encodeMemoryBarrier() {
        try (var stack = stackPush()) {
            var barrier = VkMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT);
            vkCmdPipelineBarrier(this.buffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    0, barrier, null, null);
        }
    }

    public static int dstStageToAccess(int dstStage) {
        return switch (dstStage) {
            case VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT -> VK_ACCESS_INDIRECT_COMMAND_READ_BIT;
            case VK_PIPELINE_STAGE_VERTEX_INPUT_BIT -> VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT;
            case VK_PIPELINE_STAGE_VERTEX_SHADER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_GEOMETRY_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TESSELLATION_CONTROL_SHADER_BIT, VK_PIPELINE_STAGE_TESSELLATION_EVALUATION_SHADER_BIT, VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR ->
                    VK_ACCESS_SHADER_READ_BIT;
            case VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT -> VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
            case VK_PIPELINE_STAGE_TRANSFER_BIT -> VK_ACCESS_TRANSFER_READ_BIT;
            case VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR ->
                    VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR | VK_ACCESS_SHADER_READ_BIT;
            default -> VK_ACCESS_MEMORY_READ_BIT;
        };
    }

    public static int srcStageToAccess(int srcStage) {
        return switch (srcStage) {
            case VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT -> VK_ACCESS_INDIRECT_COMMAND_READ_BIT;
            case VK_PIPELINE_STAGE_VERTEX_INPUT_BIT -> VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT;
            case VK_PIPELINE_STAGE_VERTEX_SHADER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_GEOMETRY_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TESSELLATION_CONTROL_SHADER_BIT, VK_PIPELINE_STAGE_TESSELLATION_EVALUATION_SHADER_BIT, VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR ->
                    VK_ACCESS_SHADER_WRITE_BIT;
            case VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT -> VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            case VK_PIPELINE_STAGE_TRANSFER_BIT -> VK_ACCESS_TRANSFER_WRITE_BIT;
            case VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR ->
                    VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR | VK_ACCESS_SHADER_WRITE_BIT;
            default -> VK_ACCESS_MEMORY_WRITE_BIT;
        };
    }

    public void encodeBufferBarrier(final VRef<VBuffer> buffer, long offset, long size) {
        encodeBufferBarrier(buffer, offset, size, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT);
    }

    public void encodeBufferBarrier(final VRef<VBuffer> buffer, long offset, long size, int srcStage, int dstStage) {
        try (var stack = stackPush()) {
            var barrier = VkBufferMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(srcStageToAccess(srcStage))
                    .dstAccessMask(dstStageToAccess(dstStage)).buffer(buffer.get().buffer())
                    .offset(offset).size(size);
            vkCmdPipelineBarrier(this.buffer, srcStage, dstStage,
                    0, null, barrier, null);
        }

       addBufferRef(buffer);
    }

    public int srcLayoutToStage(int srcLayout) {
        return switch (srcLayout) {
            case VK_IMAGE_LAYOUT_UNDEFINED -> VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> VK_PIPELINE_STAGE_TRANSFER_BIT;
            case VK_IMAGE_LAYOUT_GENERAL -> VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL ->
                    VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR;
            default -> VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
        };
    }

    public int layoutToAccess(int srcLayout) {
        return switch (srcLayout) {
            case VK_IMAGE_LAYOUT_UNDEFINED -> 0;
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> VK_ACCESS_TRANSFER_READ_BIT;
            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> VK_ACCESS_TRANSFER_WRITE_BIT;
            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> VK_ACCESS_SHADER_READ_BIT;
            default -> VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
        };
    }

    public int dstLayoutToStage(int dstLayout) {
        return switch (dstLayout) {
            case VK_IMAGE_LAYOUT_UNDEFINED -> VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> VK_PIPELINE_STAGE_TRANSFER_BIT;
            case VK_IMAGE_LAYOUT_GENERAL -> VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL ->
                    VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR;
            default -> VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        };
    }

    public void encodeImageTransition(VRef<VImage> image, int src, int dst, int aspectMask, int mipLevels) {
        try (var stack = stackPush()) {
            var barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().oldLayout(src).newLayout(dst).image(image.get().image())
                    .subresourceRange().aspectMask(aspectMask).baseMipLevel(0).levelCount(mipLevels).baseArrayLayer(0)
                    .layerCount(VK_REMAINING_ARRAY_LAYERS);

            int srcStage = srcLayoutToStage(src);
            int dstStage = dstLayoutToStage(dst);
            barrier.srcAccessMask(layoutToAccess(src));
            barrier.dstAccessMask(layoutToAccess(dst));

            vkCmdPipelineBarrier(this.buffer, srcStage, dstStage,
                    0, null, null, barrier);
        }
        addImageRef(image);
    }

    protected void free() {
        vkFreeCommandBuffers(pool.device, pool.pool, buffer);
        refs.clear();
    }

    public void setDebugUtilsObjectName(String name) {
        Vulkanite.INSTANCE.getCtx().setDebugUtilsObjectName(buffer.address(), VK_OBJECT_TYPE_COMMAND_BUFFER, name);
    }
}
