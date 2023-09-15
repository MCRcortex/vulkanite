package me.cortex.vulkanite.client.rendering;

import me.cortex.vulkanite.acceleration.SharedQuadVkIndexBuffer;
import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IVGImage;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.cmd.VCommandPool;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.VUtil;
import me.cortex.vulkanite.lib.other.sync.VFence;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import net.coderbot.iris.mixin.LevelRendererAccessor;
import net.coderbot.iris.vertices.ImmediateState;
import net.coderbot.iris.vertices.IrisVertexFormats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.joml.Vector3d;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class EntityRenderer {
    private final VertexCaptureProvider capture = new VertexCaptureProvider();

    //TODO: dont pass camera position of 0,0,0 in due to loss of precision
    public Pair<VAccelerationStructure, VBuffer> render(float delta, VContext ctx, VCmdBuff cmd, VFence fence) {
        LevelRendererAccessor lra = (LevelRendererAccessor) MinecraftClient.getInstance().worldRenderer;

        MatrixStack stack = new MatrixStack();

        //ImmediateState.renderWithExtendedVertexFormat = true;
        for (var entity : MinecraftClient.getInstance().world.getEntities()) {
            lra.invokeRenderEntity(entity, 0,0,0, delta, stack, capture);
        }
        //ImmediateState.renderWithExtendedVertexFormat = false;

        //Note the lifetime of the buffer data is till the next call of render
        var buffers = capture.end();
        if (buffers.isEmpty()) {
            return null;
        }
        return buildBlas(ctx, buffers, cmd, fence);
    }


    private static class VertexCaptureProvider implements VertexConsumerProvider {
        private final Map<RenderLayer, BufferBuilder> builderMap = new HashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return builderMap.compute(layer, (layer1, builder)-> {
                if (builder == null) {
                    builder = new BufferBuilder(420);
                }
                if (!builder.isBuilding()) {
                    builder.reset();
                    builder.begin(layer1.getDrawMode(), layer1.getVertexFormat());
                }
                return builder;
            });
        }

        public List<Pair<RenderLayer, BufferBuilder.BuiltBuffer>> end() {
            List<Pair<RenderLayer, BufferBuilder.BuiltBuffer>> buffers = new ArrayList<>();
            builderMap.forEach((layer,buffer)->{
                if (buffer.isBuilding()) {
                    var builtBuffer = buffer.end();
                    //TODO: Doesnt support terrian vertex format yet, requires a second blas so that the instance offset can be the same
                    // as terrain instance offset
                    if (builtBuffer.getParameters().format().equals(IrisVertexFormats.TERRAIN)) {
                        return;
                    }
                    //Dont support no texture things
                    if (((RenderLayer.MultiPhase)layer).phases.texture.getId().isEmpty()) {
                        return;
                    }
                    buffers.add(new Pair<>(layer, builtBuffer));
                }
            });
            return buffers;
        }
    }


    private record BuildInfo(VertexFormat format, int quadCount, long address) {}
    private Pair<VAccelerationStructure, VBuffer> buildBlas(VContext ctx, List<Pair<RenderLayer, BufferBuilder.BuiltBuffer>> renders, VCmdBuff cmd, VFence fence) {
        long combined_size = 0;
        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
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
            if (!type.getRight().getParameters().format().equals(IrisVertexFormats.ENTITY)) {
                throw new IllegalStateException("Unknown vertex format used");
            }
            combined_size += type.getRight().getVertexBuffer().remaining() + 256;//Add just some buffer so we can do alignment etc
        }
        //Each render layer gets its own geometry entry in the blas

        //TODO: PUT THE BINDLESS TEXTURE REFERENCE AT THE START OF THE render layers geometry buffer
        var geometryBuffer = ctx.memory.createBuffer(combined_size, VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
        long ptr = geometryBuffer.map();
        long offset = 0;
        List<BuildInfo> infos = new ArrayList<>();
        for (var pair : renders) {
            offset = VUtil.alignUp(offset, 128);
            MemoryUtil.memCopy(MemoryUtil.memAddress(pair.getRight().getVertexBuffer()), ptr + offset, pair.getRight().getVertexBuffer().remaining());
            infos.add(new BuildInfo(pair.getRight().getParameters().format(), pair.getRight().getParameters().indexCount()/6, geometryBuffer.deviceAddress() + offset));
            offset += pair.getRight().getVertexBuffer().remaining();
        }
        geometryBuffer.unmap();

        VAccelerationStructure blas;
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer primitiveCounts = stack.callocInt(infos.size());
            var buildInfo = populateBuildStructs(ctx, stack, infos, primitiveCounts);

            blas = executeBlasBuild(ctx, cmd, fence, stack, buildInfo, primitiveCounts);
        }


        return new Pair<>(blas, geometryBuffer);
    }

    private VkAccelerationStructureGeometryKHR.Buffer populateBuildStructs(VContext ctx, MemoryStack stack, List<BuildInfo> geometries, IntBuffer primitiveCounts) {
        var geometryInfos = VkAccelerationStructureGeometryKHR.calloc(geometries.size(), stack);
        for (var geometry : geometries) {
            VkDeviceOrHostAddressConstKHR indexData = SharedQuadVkIndexBuffer.getIndexBuffer(ctx, geometry.quadCount);
            int indexType = SharedQuadVkIndexBuffer.TYPE;

            VkDeviceOrHostAddressConstKHR vertexData = VkDeviceOrHostAddressConstKHR.calloc(stack).deviceAddress(geometry.address);
            int vertexFormat = VK_FORMAT_R32G32B32_SFLOAT;
            int vertexStride = geometry.format.getVertexSizeByte();

            geometryInfos.get()
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
            primitiveCounts.put(geometry.quadCount * 2);
        }
        primitiveCounts.rewind();
        return geometryInfos;
    }

    private static VAccelerationStructure executeBlasBuild(VContext ctx, VCmdBuff cmd, VFence cleanupFence, MemoryStack stack, VkAccelerationStructureGeometryKHR.Buffer geometryInfos, IntBuffer maxPrims) {
        var buildInfos = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack);
        PointerBuffer buildRanges = stack.mallocPointer(1);

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
                maxPrims,
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

        vkCmdBuildAccelerationStructuresKHR(cmd.buffer, buildInfos, buildRanges);

        ctx.sync.addCallback(cleanupFence, scratch::free);
        return structure;
    }
}
