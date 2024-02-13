package me.cortex.vulkanite.mixin.minecraft;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IVGImage;
import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.memory.VGImage;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.resource.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;

import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;

@Mixin(ResourceTexture.class)
public abstract class MixinResourceTexture extends AbstractTexture implements IVGImage {
    @Redirect(method = "upload", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextureUtil;prepareImage(IIII)V"))
    private void redirect(int id, int maxLevel, int width, int height) {
        GlStateManager._bindTexture(0);
        RenderSystem.assertOnRenderThreadOrInit();
        if (getVGImage() != null) {
            System.err.println("Vulkan image already allocated, releasing");
            setVGImage(null);
        }

        if (glId != -1) {
            glDeleteTextures(glId);
            glId = -1;
        }
        var img = Vulkanite.INSTANCE.getCtx().memory.createSharedImage(
                width,
                height,
                maxLevel + 1,
                VK_FORMAT_R8G8B8A8_UNORM,
                GL_RGBA8,
                VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        setVGImage(img);

        Vulkanite.INSTANCE.getCtx().cmd.executeWait(cmdbuf -> {
            cmdbuf.encodeImageTransition(new VRef<>(img.get()), VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);
        });

        GlStateManager._bindTexture(getGlId());
        if (maxLevel >= 0) {
            GlStateManager._texParameter(3553, 33085, maxLevel);
            GlStateManager._texParameter(3553, 33082, 0);
            GlStateManager._texParameter(3553, 33083, maxLevel);
            GlStateManager._texParameter(3553, 34049, 0.0F);
        }
    }
}
