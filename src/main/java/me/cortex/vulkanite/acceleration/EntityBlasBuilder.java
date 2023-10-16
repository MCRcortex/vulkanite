package me.cortex.vulkanite.acceleration;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.srp.lua.LuaExternalObjects;
import me.cortex.vulkanite.compat.IVGImage;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.descriptors.DescriptorSetLayoutBuilder;
import me.cortex.vulkanite.lib.descriptors.DescriptorUpdateBuilder;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSetLayout;
import me.cortex.vulkanite.lib.descriptors.VTypedDescriptorPool;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.memory.VImage;
import me.cortex.vulkanite.lib.other.VImageView;
import me.cortex.vulkanite.lib.other.VSampler;
import me.cortex.vulkanite.lib.other.VUtil;
import me.cortex.vulkanite.lib.other.sync.VFence;
import net.coderbot.iris.vertices.IrisVertexFormats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Pair;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT;

public class EntityBlasBuilder {
    private static VDescriptorSetLayout ENTITY_LAYOUT;
    private static VTypedDescriptorPool ENTITY_POOL;
    private static VSampler SAMPLER;
    private final VContext ctx;
    public EntityBlasBuilder(VContext context) {
        this.ctx = context;
        if (ENTITY_LAYOUT == null) {
            ENTITY_LAYOUT = new DescriptorSetLayoutBuilder()
                    .binding(0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 32, VK_SHADER_STAGE_ALL)
                    .binding(1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 32, VK_SHADER_STAGE_ALL)
                    .setBindingFlags(0, VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT)
                    .setBindingFlags(1, VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT)
                    .build(context);
            ENTITY_POOL = new VTypedDescriptorPool(ctx, ENTITY_LAYOUT, 0);
            SAMPLER = new VSampler(context, a->a.magFilter(VK_FILTER_NEAREST)
                    .minFilter(VK_FILTER_NEAREST)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .compareOp(VK_COMPARE_OP_NEVER)
                    .maxLod(1)
                    .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .maxAnisotropy(1.0f));
        }
    }

    private record BuildInfo(VertexFormat format, int quadCount, long address) {}
    Pair<VAccelerationStructure, VBuffer> buildBlas(List<Pair<RenderLayer, BufferBuilder.BuiltBuffer>> renders, VCmdBuff cmd, VFence fence) {
        var descriptorSet = ENTITY_POOL.allocateSet();
        ctx.sync.addCallback(fence, ()->Vulkanite.INSTANCE.addSyncedCallback(descriptorSet::free));
        LuaExternalObjects.ENTITY_DATA_LAYOUT.setConcrete(descriptorSet.set);

        long combined_size = 0;
        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
        List<VImage> images = new ArrayList<>(renders.size());
        for (var type : renders) {
            if (((RenderLayer.MultiPhase)type.getLeft()).phases.texture instanceof RenderPhase.Textures) {
                throw new IllegalStateException("Multi texture not supported");
            }
            var textureId = ((RenderLayer.MultiPhase)type.getLeft()).phases.texture.getId().get();
            var texture = textureManager.getTexture(textureId);
            var vkImage = ((IVGImage)texture).getVGImage();
            if (vkImage == null) {
                throw new IllegalStateException("Vulkan texture not created for render layer " + type.getLeft());
            }
            images.add(vkImage);
            if (!type.getRight().getParameters().format().equals(IrisVertexFormats.ENTITY)) {
                throw new IllegalStateException("Unknown vertex format used");
            }
            combined_size += type.getRight().getVertexBuffer().remaining() + 256;//Add just some buffer so we can do alignment etc
        }
        //Each render layer gets its own geometry entry in the blas

        //TODO: PUT THE BINDLESS TEXTURE REFERENCE AT THE START OF THE render layers geometry buffer
        var geometryBuffer = ctx.memory.createBuffer(combined_size, VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR | VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
        long ptr = geometryBuffer.map();
        long offset = 0;
        List<BuildInfo> infos = new ArrayList<>();
        var dub = new DescriptorUpdateBuilder(ctx, 100)
                .set(descriptorSet.set);

        for (int i = 0; i < images.size(); i++) {
            //TODO: FIXME: OPTIMIZE
            var view = new VImageView(ctx, images.get(i));
            dub.imageSampler(0, i, view, SAMPLER);
            //Pain, this is to ensure the gpu is done with it cause we dont have a reference to a fence we can use (that is synced with the primary trace)
            ctx.sync.addCallback(fence, ()->Vulkanite.INSTANCE.addSyncedCallback(()->Vulkanite.INSTANCE.addSyncedCallback(view::free)));
        }



        int i = 0;
        for (var pair : renders) {
            offset = VUtil.alignUp(offset, 128);
            dub.buffer(1, i++, geometryBuffer, offset, pair.getRight().getVertexBuffer().remaining() + 32);

            MemoryUtil.memCopy(MemoryUtil.memAddress(pair.getRight().getVertexBuffer()), ptr + offset, pair.getRight().getVertexBuffer().remaining());
            infos.add(new BuildInfo(pair.getRight().getParameters().format(), pair.getRight().getParameters().indexCount()/6, geometryBuffer.deviceAddress() + offset));

            offset += 64;
            offset += pair.getRight().getVertexBuffer().remaining();
        }

        dub.apply();

        geometryBuffer.unmap();

        VAccelerationStructure blas = null;
        try (var stack = MemoryStack.stackPush()) {
            int[] primitiveCounts = new int[infos.size()];
            var buildInfo = populateBuildStructs(ctx, stack, cmd, infos, primitiveCounts);

            blas = executeBlasBuild(ctx, cmd, fence, stack, buildInfo, primitiveCounts);
        }


        return new Pair<>(blas, geometryBuffer);
    }

    private VkAccelerationStructureGeometryKHR.Buffer populateBuildStructs(VContext ctx, MemoryStack stack, VCmdBuff cmdBuff, List<BuildInfo> geometries, int[] primitiveCounts) {
        var geometryInfos = VkAccelerationStructureGeometryKHR.calloc(geometries.size(), stack);
        int i = 0;
        for (var geometry : geometries) {
            VkDeviceOrHostAddressConstKHR indexData = SharedQuadVkIndexBuffer.getIndexBuffer(ctx, cmdBuff, geometry.quadCount);
            int indexType = SharedQuadVkIndexBuffer.TYPE;

            VkDeviceOrHostAddressConstKHR vertexData = VkDeviceOrHostAddressConstKHR.calloc(stack).deviceAddress(geometry.address);
            int vertexFormat = VK_FORMAT_R32G32B32_SFLOAT;
            int vertexStride = geometry.format.getVertexSizeByte();

            geometryInfos.get()
                    .sType$Default()
                    .geometry(VkAccelerationStructureGeometryDataKHR.calloc(stack)
                            .triangles(VkAccelerationStructureGeometryTrianglesDataKHR.calloc(stack)
                                    .sType$Default()

                                    .vertexData(vertexData)
                                    .vertexFormat(vertexFormat)
                                    .vertexStride(vertexStride)
                                    .maxVertex(geometry.quadCount * 4)

                                    .indexData(indexData)
                                    .indexType(indexType)))
                    .geometryType(VK_GEOMETRY_TYPE_TRIANGLES_KHR)
            //        .flags(geometry.geometryFlags)
            ;
            primitiveCounts[i++] = (geometry.quadCount * 2);
        }
        geometryInfos.rewind();
        return geometryInfos;
    }

    private static VAccelerationStructure executeBlasBuild(VContext ctx, VCmdBuff cmd, VFence cleanupFence, MemoryStack stack, VkAccelerationStructureGeometryKHR.Buffer geometryInfos, int[] prims) {
        var buildInfos = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack);
        var buildRanges = VkAccelerationStructureBuildRangeInfoKHR.calloc(prims.length, stack);
        for (int primCount : prims) {
            buildRanges.get().primitiveCount(primCount);
        }

        var bi = buildInfos.get()
                .sType$Default()
                .type(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR)
                .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_BUILD_BIT_KHR)
                .pGeometries(geometryInfos)
                .geometryCount(geometryInfos.remaining());

        VkAccelerationStructureBuildSizesInfoKHR buildSizesInfo = VkAccelerationStructureBuildSizesInfoKHR
                .calloc(stack)
                .sType$Default();

        vkGetAccelerationStructureBuildSizesKHR(
                ctx.device,
                VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                bi,
                prims,
                buildSizesInfo);


        var structure = ctx.memory.createAcceleration(buildSizesInfo.accelerationStructureSize(), 256,
                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR, VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR);

        var scratch = ctx.memory.createBuffer(buildSizesInfo.buildScratchSize(),
                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 256, 0);

        bi.scratchData(VkDeviceOrHostAddressKHR.calloc(stack).deviceAddress(scratch.deviceAddress()));
        bi.dstAccelerationStructure(structure.structure);

        buildInfos.rewind();
        buildRanges.rewind();

        vkCmdBuildAccelerationStructuresKHR(cmd.buffer, buildInfos, stack.pointers(buildRanges));

        vkCmdPipelineBarrier(cmd.buffer, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, 0, VkMemoryBarrier.calloc(1)
                .sType$Default()
                .srcAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR)
                .dstAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR), null, null);

        ctx.sync.addCallback(cleanupFence, scratch::free);
        return structure;
    }
}
