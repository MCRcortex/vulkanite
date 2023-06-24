package me.cortex.vulkanite.mixin;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.compile.ChunkBuildContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderRebuildTask;
import me.jellysquid.mods.sodium.client.util.task.CancellationSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Collectors;

@Mixin(value = ChunkRenderRebuildTask.class, remap = false)
public class MixinChunkRenderRebuildTask {
    @Inject(method = "performBuild", at = @At("TAIL"))
    private void performExtraBuild(ChunkBuildContext buildContext, CancellationSource cancellationSource, CallbackInfoReturnable<ChunkBuildResult> cir) {
        var buildResult = cir.getReturnValue();
        var ebr = (IAccelerationBuildResult) buildResult;
        for (var mesh : buildResult.meshes.entrySet()) {
            GlVertexFormat<ChunkMeshAttribute> format = (GlVertexFormat<ChunkMeshAttribute>)mesh.getValue().getVertexData().vertexFormat();
            System.err.println(format);
        }
    }
}
