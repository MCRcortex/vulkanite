package me.cortex.vulkanite.mixin.sodium;

import me.cortex.vulkanite.client.Vulkanite;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

//The process needs to go like this
//(This is for if a chunk mesh already exists)
//Make a copy of the old chunk mesh
// set that as the primary reference pointer
//Once the chunk update blas job is built and processed
// destroy the old pointer
@Mixin(value = RenderRegionManager.class, remap = false)
public class MixinRenderRegionManager {
    @Inject(method = "uploadMeshes(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;)V",
            at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferArena;upload(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/stream/Stream;)Z",
                    shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void afterUpload(CommandList commandList, RenderRegion region, Collection<ChunkBuildOutput> results, CallbackInfo ci, ArrayList<PendingSectionUploadAccessor> uploads, RenderRegion.DeviceResources resources, GlBufferArena arena) {
        for (var res : results) {
            //((IRenderSectionExtension)(res.render)).setArena(region.getResources().getGeometryArena());
            //((IRenderSectionExtension)(res.render)).getAllocations().clear();
        }

        for (PendingSectionUploadAccessor uploadObj : uploads) {
            //((IRenderSectionExtension)(uploadObj.getSection())).getAllocations().add(uploadObj.getVertexUpload().getResult());
        }
    }

    @Inject(method = "uploadMeshes(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;)V",
            at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegion;refresh(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;)V",
                    shift = At.Shift.AFTER))
    private void arenaBufferChanged(CommandList commandList, RenderRegion region, Collection<ChunkBuildOutput> results, CallbackInfo ci) {
        List<RenderSection> sectionsInRegion = new ArrayList<>();
        for (int id = 0; id < 256; id++) {
            if (region.getSection(id) != null) {
                sectionsInRegion.add(region.getSection(id));
            }
        }
    }
}
