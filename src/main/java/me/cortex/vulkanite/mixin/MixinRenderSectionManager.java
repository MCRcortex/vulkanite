package me.cortex.vulkanite.mixin;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import me.cortex.vulkanite.client.IAccelerationBuildResult;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class MixinRenderSectionManager {
    @Shadow @Final private Long2ReferenceMap<RenderSection> sectionByPosition;

    @Inject(method = "destroy", at = @At("HEAD"))
    private void onDestroy(CallbackInfo ci) {
        for (var section : sectionByPosition.values()) {
            Vulkanite.INSTANCE.sectionRemove(section);
        }
    }

    @Inject(method = "processChunkBuildResults", at = @At("HEAD"))
    private void processResults(ArrayList<ChunkBuildOutput> results, CallbackInfo ci) {
        Reference2ReferenceLinkedOpenHashMap<RenderSection, ChunkBuildOutput> map = new Reference2ReferenceLinkedOpenHashMap<>();
        for(ChunkBuildOutput output : results) {
            if (!output.render.isDisposed() && output.render.getLastBuiltFrame() <= output.buildTime) {
                RenderSection render = output.render;
                ChunkBuildOutput previous = map.get(render);
                if (previous == null || previous.buildTime < output.buildTime) {
                    var prev = map.put(render, output);
                    if (prev != null) {
                        var data = ((IAccelerationBuildResult)output).getAccelerationGeometryData();
                        if (data != null)
                            data.values().forEach(NativeBuffer::free);
                    }
                } else {
                    //Else need to free the injected result
                    var data = ((IAccelerationBuildResult)output).getAccelerationGeometryData();
                    if (data != null)
                        data.values().forEach(NativeBuffer::free);
                }
            } else {
                //Else need to free the injected result
                var data = ((IAccelerationBuildResult)output).getAccelerationGeometryData();
                if (data != null)
                    data.values().forEach(NativeBuffer::free);
            }
        }
        Vulkanite.INSTANCE.upload(new ArrayList<>(map.values()));
    }
}
