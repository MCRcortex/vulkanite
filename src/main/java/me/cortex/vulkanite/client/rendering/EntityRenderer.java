package me.cortex.vulkanite.client.rendering;

import net.coderbot.iris.mixin.LevelRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

public class EntityRenderer {
    private final VertexCaptureProvider capture = new VertexCaptureProvider();

    //TODO: dont pass camera position of 0,0,0 in due to loss of precision
    public void render(float delta) {
        LevelRendererAccessor lra = (LevelRendererAccessor) MinecraftClient.getInstance().worldRenderer;

        MatrixStack stack = new MatrixStack();
        for (var entity : MinecraftClient.getInstance().world.getEntities()) {
            lra.invokeRenderEntity(entity, 0,0,0, delta, stack, capture);
        }

        capture.end();
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

        public void end() {
            builderMap.forEach((layer,buffer)->{
                if (buffer.isBuilding()) {
                    buffer.end();
                }
            });
        }
    }
}
