package me.cortex.vulkanite.mixin.iris;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IVGImage;
import me.cortex.vulkanite.lib.memory.VGImage;
import me.cortex.vulkanite.lib.other.FormatConverter;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.texture.GlTexture;
import net.coderbot.iris.gl.texture.InternalTextureFormat;
import net.coderbot.iris.gl.texture.TextureType;
import net.coderbot.iris.shaderpack.texture.TextureFilteringData;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.vulkan.VK10.*;

@Mixin(value = GlTexture.class, remap = false)
public abstract class MixinGlTexture extends MixinGlResource implements IVGImage {
    @Unique private VGImage sharedImage;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_genTexture()I"))
    private static int redirectGen() {
        return -1;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/gl/texture/GlTexture;getGlId()I", ordinal = 0))
    private int redirectTextureCreation(GlTexture instance, TextureType target, int sizeX, int sizeY, int sizeZ, int internalFormat, int format, int pixelType, byte[] pixels, TextureFilteringData filteringData) {
        // Before getting the texture id, create the texture that wasn't created earlier

        InternalTextureFormat textureFormat = FormatConverter.findFormatFromGlFormat(internalFormat);
        int vkFormat = FormatConverter.getVkFormatFromGl(textureFormat);

        // Clamp y,z to 1 as per VK spec
        sizeY = Math.max(sizeY, 1);
        sizeZ = Math.max(sizeZ, 1);

        sharedImage = Vulkanite.INSTANCE.getCtx().memory
            .createSharedImage(
                    (sizeZ == 1 && sizeY == 1? 1 : (sizeZ == 1?2:3)),
                    sizeX,
                    sizeY,
                    sizeZ,
                    1,
                    vkFormat,
                    textureFormat.getGlFormat(),
                    VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );
        sharedImage.setDebugUtilsObjectName("GlTexture");

        Vulkanite.INSTANCE.getCtx().cmd.executeWait(cmdbuf -> {
            cmdbuf.encodeImageTransition(sharedImage, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);
        });

        this.setGlId(sharedImage.glId);

        return sharedImage.glId;
    }

    @Redirect(method="<init>", at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/gl/texture/TextureType;apply(IIIIIIILjava/nio/ByteBuffer;)V"))
    private void redirectUpload(TextureType instance, int glId, int width, int height, int depth, int internalFormat, int format, int pixelType, ByteBuffer data) {
        int target = instance.getGlType();

        RenderSystem.assertOnRenderThreadOrInit();
        IrisRenderSystem.bindTextureForSetup(target, glId);

        switch (instance) {
            case TEXTURE_1D:
                GL30.glTexSubImage1D(target, 0, 0, width, format, pixelType, data);
                break;
            case TEXTURE_2D, TEXTURE_RECTANGLE:
                GL30.glTexSubImage2D(target, 0, 0, 0, width, height, format, pixelType, data);
                break;
            case TEXTURE_3D:
                GL30.glTexSubImage3D(target, 0, 0, 0, 0, width, height, depth, format, pixelType, data);
                break;
        }
    }

    @Overwrite
    protected void destroyInternal(){
        glFinish();
        sharedImage.free();
    }

    public VGImage getVGImage() {
        return sharedImage;
    }
}
