package me.cortex.vulkanite.mixin.sodium;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import me.cortex.vulkanite.compat.IAccelerationBuildResult;
import me.cortex.vulkanite.client.Vulkanite;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class MixinRenderSectionManager {
    @Shadow @Final private Long2ReferenceMap<RenderSection> sectionByPosition;

    @Inject(method = "destroy", at = @At("HEAD"))
    private void onDestroy(CallbackInfo ci) {
        for (var section : sectionByPosition.values()) {
            Vulkanite.INSTANCE.sectionRemove(section);
        }
        Vulkanite.INSTANCE.destroy();
    }

    @Redirect(method = "destroy", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;delete()V"))
    private void destroyAccelerationData(ChunkBuildOutput instance) {
        var data = ((IAccelerationBuildResult)instance).getAccelerationGeometryData();
        if (data != null) {
            data.values().forEach(entry->entry.data().free());
        }
        instance.delete();
        //TODO: need to ingest and cleanup all the blas builds and tlas updates
    }

    @Inject(method = "processChunkBuildResults", at = @At("HEAD"))
    private void processResults(ArrayList<ChunkBuildOutput> results, CallbackInfo ci) {
        Reference2ReferenceLinkedOpenHashMap<RenderSection, ChunkBuildOutput> map = new Reference2ReferenceLinkedOpenHashMap<>();
        for(ChunkBuildOutput output : results) {
            if (((IAccelerationBuildResult)output).getAccelerationGeometryData() == null)
                continue;
            if (!output.render.isDisposed() && output.render.getLastBuiltFrame() <= output.buildTime) {
                RenderSection render = output.render;
                ChunkBuildOutput previous = map.get(render);
                if (previous == null || previous.buildTime < output.buildTime) {
                    var prev = map.put(render, output);
                    if (prev != null) {
                        var data = ((IAccelerationBuildResult)output).getAccelerationGeometryData();
                        data.values().forEach(a->a.data().free());
                    }
                } else {
                    //Else need to free the injected result
                    var data = ((IAccelerationBuildResult)output).getAccelerationGeometryData();
                    data.values().forEach(a->a.data().free());
                }
            } else {
                //Else need to free the injected result
                var data = ((IAccelerationBuildResult)output).getAccelerationGeometryData();
                data.values().forEach(a->a.data().free());
            }
        }
        if (!map.values().isEmpty()) {
            Vulkanite.INSTANCE.upload(new ArrayList<>(map.values()));
        }
    }
}
