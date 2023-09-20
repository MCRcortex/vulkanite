package me.cortex.vulkanite.mixin.sodium.gl;

import me.cortex.vulkanite.compat.IVGBuffer;
import me.cortex.vulkanite.compat.IVulkanContextGetter;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.memory.VGBuffer;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferUsage;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;

@Mixin(value = GlBufferArena.class, remap = false)
public class MixinGlBufferArena {

    @Unique
    private VGBuffer createBuffer(VContext ctx, long size) {
        return ctx.memory.createSharedBuffer(size,  VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/gl/device/CommandList;allocateStorage(Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;JLme/jellysquid/mods/sodium/client/gl/buffer/GlBufferUsage;)V"))
    private void redirectBufferInit(CommandList instance, GlMutableBuffer buffer, long size, GlBufferUsage usage) {
        var vkglbuff = (IVGBuffer)buffer;
        var ctx = ((IVulkanContextGetter)instance).getCtx();
        vkglbuff.setBuffer(createBuffer(ctx, size));
    }

    @Redirect(method = "transferSegments", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/gl/device/CommandList;allocateStorage(Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;JLme/jellysquid/mods/sodium/client/gl/buffer/GlBufferUsage;)V"))
    private void redirectBufferTransfer(CommandList instance, GlMutableBuffer buffer, long size, GlBufferUsage usage) {
        var vkglbuff = (IVGBuffer)buffer;
        var ctx = ((IVulkanContextGetter)instance).getCtx();
        vkglbuff.setBuffer(createBuffer(ctx, size));
    }
}
