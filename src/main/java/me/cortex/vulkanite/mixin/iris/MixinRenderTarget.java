package me.cortex.vulkanite.mixin.iris;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IRenderTargetVkGetter;
import me.cortex.vulkanite.lib.memory.VGImage;
import me.cortex.vulkanite.lib.other.FormatConverter;
import net.coderbot.iris.gl.texture.InternalTextureFormat;
import net.coderbot.iris.gl.texture.PixelFormat;
import net.coderbot.iris.rendertarget.RenderTarget;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.vulkan.VK10.*;

@Mixin(value = RenderTarget.class, remap = false)
public abstract class MixinRenderTarget implements IRenderTargetVkGetter {
    @Shadow @Final private PixelFormat format;
    @Shadow @Final private InternalTextureFormat internalFormat;

    @Shadow protected abstract void setupTexture(int i, int i1, int i2, boolean b);

    @Unique private VGImage vgMainTexture;
    @Unique private VGImage vgAltTexture;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_genTextures([I)V"))
    private void redirectGen(int[] textures) {

    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/rendertarget/RenderTarget;setupTexture(IIIZ)V", ordinal = 0))
    private void redirectMain(RenderTarget instance, int id, int width, int height, boolean allowsLinear) {
        setupTextures(width, height, allowsLinear);
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/rendertarget/RenderTarget;setupTexture(IIIZ)V", ordinal = 1))
    private void redirectAlt(RenderTarget instance, int id, int width, int height, boolean allowsLinear) {}

    @Redirect(method = "setupTexture", at = @At(value = "INVOKE",target = "Lnet/coderbot/iris/rendertarget/RenderTarget;resizeTexture(III)V"))
    private void redirectResize(RenderTarget instance, int t, int w, int h) {}

    @Overwrite
    public int getMainTexture() {
        return vgMainTexture.glId;
    }

    @Overwrite
    public int getAltTexture() {
        return vgAltTexture.glId;
    }

    @Overwrite
    public void resize(int width, int height) {
        glFinish();
        //TODO: block the gpu fully before deleting and resizing the textures
        vgMainTexture.free();
        vgAltTexture.free();

        setupTextures(width, height, !internalFormat.getPixelFormat().isInteger());
    }

    private void setupTextures(int width, int height, boolean allowsLinear) {
        var ctx = Vulkanite.INSTANCE.getCtx();

        int glfmt = internalFormat.getGlFormat();
        glfmt = (glfmt == GL_RGBA) ? GL_RGBA8 : glfmt;

        int vkfmt = FormatConverter.getVkFormatFromGl(internalFormat);

        vgMainTexture = ctx.memory.createSharedImage(width, height, 1, vkfmt, glfmt, VK_IMAGE_USAGE_STORAGE_BIT , VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        vgAltTexture = ctx.memory.createSharedImage(width, height, 1, vkfmt, glfmt, VK_IMAGE_USAGE_STORAGE_BIT , VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        vgMainTexture.setDebugUtilsObjectName("RenderTarget Main");
        vgAltTexture.setDebugUtilsObjectName("RenderTarget Alt");
        Vulkanite.INSTANCE.getCtx().cmd.executeWait(cmdbuf -> {
            cmdbuf.encodeImageTransition(vgMainTexture, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);
            cmdbuf.encodeImageTransition(vgAltTexture, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);
        });

        setupTexture(getMainTexture(), width, height, allowsLinear);
        setupTexture(getAltTexture(), width, height, allowsLinear);
    }

    @Redirect(method = "destroy", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_deleteTextures([I)V"))
    private void redirectResize(int[] textures) {
        glFinish();
        //TODO: block the gpu fully before deleting and resizing the textures
        vgMainTexture.free();
        vgAltTexture.free();
    }

    public VGImage getMain() {
        return vgMainTexture;
    }

    public VGImage getAlt() {
        return vgAltTexture;
    }
}
