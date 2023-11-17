package me.cortex.vulkanite.mixin.iris;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IVGImage;
import me.cortex.vulkanite.mixin.minecraft.MixinAbstractTexture;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.rendertarget.NativeImageBackedCustomTexture;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.vulkan.VK10.*;

@Mixin(value = NativeImageBackedTexture.class, remap = false)
public abstract class MixinNativeImageBackedTexture extends AbstractTexture implements IVGImage {
    @Redirect(remap = true, method = "<init>(Lnet/minecraft/client/texture/NativeImage;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextureUtil;prepareImage(III)V"))
    private void redirectGen(int id, int width, int height) {
        if(!((Object) this instanceof NativeImageBackedCustomTexture)) {
            TextureUtil.prepareImage(id, width, height);
            return;
        }

        if (glId != -1) {
            glDeleteTextures(glId);
            glId = -1;
        }
        if (getVGImage() != null) {
            System.err.println("Vulkan image already allocated, releasing");
            Vulkanite.INSTANCE.addSyncedCallback(getVGImage()::free);
            setVGImage(null);
        }

        var img = Vulkanite.INSTANCE.getCtx().memory.createSharedImage(
                width,
                height,
                1,
                VK_FORMAT_R8G8B8A8_UNORM,
                GL_RGBA8,
                VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        img.setDebugUtilsObjectName("NativeImageBackedTexture");
        setVGImage(img);

        Vulkanite.INSTANCE.getCtx().cmd.executeWait(cmdbuf -> {
            cmdbuf.encodeImageTransition(img, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);
        });

        GlStateManager._bindTexture(img.glId);
    }
}
