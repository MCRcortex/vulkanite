package me.cortex.vulkanite.lib.cmd;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import me.cortex.vulkanite.lib.memory.MemoryManager;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.memory.VImage;

import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;

//TODO: Track with TrackedResourceObject but need to be careful due to how the freeing works
public class VCmdBuff extends TrackedResourceObject implements Pointer {
    private final VCommandPool pool;
    public final VkCommandBuffer buffer;

    private HashSet<TrackedResourceObject> transientResources;

    VCmdBuff(VCommandPool pool, VkCommandBuffer buff) {
        this.pool = pool;
        this.buffer = buff;
        this.transientResources = new HashSet<>();
    }

    //Enqueues the pool to be freed by the owning thread
    public void enqueueFree() {
        pool.free(this);
    }

    public void begin(int flags) {
        try (var stack = stackPush()) {
            vkBeginCommandBuffer(buffer, VkCommandBufferBeginInfo.calloc(stack).sType$Default().flags(flags));
        }
    }

    public void end() {
        vkEndCommandBuffer(buffer);
    }

    public void encodeDataUpload(MemoryManager manager, long src, VBuffer dest, long destOffset, long size) {
        VBuffer staging = manager.createBuffer(size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, 0,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
        staging.setDebugUtilsObjectName("Data Upload Host Staging");
        long ptr = staging.map();
        MemoryUtil.memCopy(src, ptr, size);
        staging.unmap();

        try (var stack = stackPush()) {
            var copy = VkBufferCopy.calloc(1, stack);
            copy.get(0).srcOffset(0).dstOffset(destOffset).size(size);
            vkCmdCopyBuffer(buffer, staging.buffer(), dest.buffer(), copy);
        }

        transientResources.add(staging);
    }

    public void encodeImageUpload(MemoryManager manager, long src, VImage dest, long srcSize, int destLayout) {
        VBuffer staging = manager.createBuffer(srcSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, 0,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
        staging.setDebugUtilsObjectName("Image Upload Host Staging");
        long ptr = staging.map();
        MemoryUtil.memCopy(src, ptr, srcSize);
        staging.unmap();

        try (var stack = stackPush()) {
            var copy = VkBufferImageCopy.calloc(1, stack);
            copy.get(0).bufferOffset(0).bufferImageHeight(0).bufferRowLength(0)
                    .imageOffset(o -> o.set(0, 0, 0))
                    .imageExtent(extent -> extent.set(dest.width, dest.height, dest.depth))
                    .imageSubresource(s -> s.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseArrayLayer(0).layerCount(1).mipLevel(0));
            vkCmdCopyBufferToImage(buffer, staging.buffer(), dest.image(), destLayout, copy);
        }

        transientResources.add(staging);
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

    public void addTransientResource(TrackedResourceObject resource) {
        transientResources.add(resource);
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

    public void encodeBufferBarrier(VBuffer buffer, long offset, long size) {
        encodeBufferBarrier(buffer, offset, size, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT);
    }

    public void encodeBufferBarrier(VBuffer buffer, long offset, long size, int srcStage, int dstStage) {
        try (var stack = stackPush()) {
            var barrier = VkBufferMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(srcStageToAccess(srcStage))
                    .dstAccessMask(dstStageToAccess(dstStage)).buffer(buffer.buffer())
                    .offset(offset).size(size);
            vkCmdPipelineBarrier(this.buffer, srcStage, dstStage,
                    0, null, barrier, null);
        }
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

    public void encodeImageTransition(VImage image, int src, int dst, int aspectMask, int mipLevels) {
        try (var stack = stackPush()) {
            var barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().oldLayout(src).newLayout(dst).image(image.image())
                    .subresourceRange().aspectMask(aspectMask).baseMipLevel(0).levelCount(mipLevels).baseArrayLayer(0)
                    .layerCount(VK_REMAINING_ARRAY_LAYERS);

            int srcStage = srcLayoutToStage(src);
            int dstStage = dstLayoutToStage(dst);
            barrier.srcAccessMask(layoutToAccess(src));
            barrier.dstAccessMask(layoutToAccess(dst));

            vkCmdPipelineBarrier(this.buffer, srcStage, dstStage,
                    0, null, null, barrier);
        }
    }

    @Override
    public long address() {
        return buffer.address();
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }

    void freeInternal() {
        free0();
        vkFreeCommandBuffers(pool.device, pool.pool, buffer);
        transientResources.forEach(TrackedResourceObject::free);
        transientResources.clear();
    }

    public void setDebugUtilsObjectName(String name) {
        Vulkanite.INSTANCE.getCtx().setDebugUtilsObjectName(buffer.address(), VK_OBJECT_TYPE_COMMAND_BUFFER, name);
    }
}
