package me.cortex.vulkanite.client.rendering;

import me.cortex.vulkanite.acceleration.SharedQuadVkIndexBuffer;
import me.cortex.vulkanite.compat.IVGImage;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.VUtil;
import me.cortex.vulkanite.lib.other.sync.VFence;
import net.coderbot.iris.mixin.LevelRendererAccessor;
import net.coderbot.iris.vertices.IrisVertexFormats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Pair;
import net.minecraft.world.World;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class EntityCapture {
    private final VertexCaptureProvider capture = new VertexCaptureProvider();

    //TODO: dont pass camera position of 0,0,0 in due to loss of precision
    public List<Pair<RenderLayer, BufferBuilder.BuiltBuffer>> capture(float delta, ClientWorld world) {
        LevelRendererAccessor lra = (LevelRendererAccessor) MinecraftClient.getInstance().worldRenderer;

        MatrixStack stack = new MatrixStack();

        //ImmediateState.renderWithExtendedVertexFormat = true;
        for (var entity : world.getEntities()) {
            lra.invokeRenderEntity(entity, 0,0,0, delta, stack, capture);
        }
        //ImmediateState.renderWithExtendedVertexFormat = false;

        //Note the lifetime of the buffer data is till the next call of render
        var buffers = capture.end();
        if (buffers.isEmpty()) {
            return null;
        }
        return buffers;
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
                    if (builtBuffer.getParameters().getBufferSize() == 0) {
                        return;//Dont add empty buffers
                    }
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
}

