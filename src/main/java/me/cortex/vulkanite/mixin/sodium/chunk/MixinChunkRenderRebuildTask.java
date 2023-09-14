package me.cortex.vulkanite.mixin.sodium.chunk;

import me.cortex.vulkanite.compat.IAccelerationBuildResult;
import me.cortex.vulkanite.compat.SodiumResultAdapter;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import me.jellysquid.mods.sodium.client.util.task.CancellationToken;
import net.irisshaders.iris.api.v0.IrisApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public class MixinChunkRenderRebuildTask {
    @Inject(method = "execute(Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lme/jellysquid/mods/sodium/client/util/task/CancellationToken;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At("TAIL"))
    private void performExtraBuild(ChunkBuildContext buildContext, CancellationToken cancellationToken, CallbackInfoReturnable<ChunkBuildOutput> cir) {
        if (IrisApi.getInstance().isShaderPackInUse()) {
            var buildResult = cir.getReturnValue();
            ((IAccelerationBuildResult) buildResult).setVertexFormat(((VertexFormatAccessor) buildContext.buffers).getVertexType());
            SodiumResultAdapter.compute(buildResult);
        }
    }
}
