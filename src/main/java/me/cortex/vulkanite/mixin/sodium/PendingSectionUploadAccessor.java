package me.cortex.vulkanite.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.arena.PendingUpload;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = {"me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager$PendingSectionUpload"}, remap = false)
public interface PendingSectionUploadAccessor {
    @Accessor
    RenderSection getSection();
    @Accessor
    TerrainRenderPass getPass();
    @Accessor
    PendingUpload getVertexUpload();
}
