package me.cortex.vulkanite.mixin.iris;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IRenderTargetVkGetter;
import me.cortex.vulkanite.lib.memory.VGImage;
import net.coderbot.iris.gl.GlResource;
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

@Mixin(value = GlResource.class, remap = false)
public abstract class MixinGlResource {
    @Shadow private boolean isValid;

    @Shadow protected abstract void assertValid();

    @Unique private int newId;

    @Inject(method = "<init>", at=@At("TAIL"))
    protected void GlResource(int id, CallbackInfo ci) {
        this.newId = id;
        if(id == -1) {
            isValid = false;
        }
    }

    protected void setGlId(int id) {
        if(this.newId == -1) {
            this.newId = id;
            isValid = true;
        }
    }

    @Overwrite
    protected int getGlId(){
        assertValid();
        return this.newId;
    }
}
