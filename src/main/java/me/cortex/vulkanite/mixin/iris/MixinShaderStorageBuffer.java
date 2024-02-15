package me.cortex.vulkanite.mixin.iris;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IVGBuffer;
import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.memory.VGBuffer;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.buffer.ShaderStorageBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.lwjgl.opengl.GL15C.glDeleteBuffers;

@Mixin(value = ShaderStorageBuffer.class, remap = false)
public class MixinShaderStorageBuffer implements IVGBuffer {
    @Shadow protected int id;
    @Unique
    private VRef<VGBuffer> vkBuffer;

    public VRef<VGBuffer> getBuffer() {
        return vkBuffer == null ? null : vkBuffer.addRef();
    }

    public void setBuffer(VRef<VGBuffer> buffer) {
        if (vkBuffer != null && buffer != null) {
            throw new IllegalStateException("Override buffer not null");
        }
        this.vkBuffer = buffer;
        if (buffer != null) {
            glDeleteBuffers(id);
            id = vkBuffer.get().glId;
        }
    }

    @Redirect(method = "destroy", at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/gl/IrisRenderSystem;deleteBuffers(I)V"))
    private void redirectDelete(int id) {
        if (vkBuffer != null) {
            vkBuffer = null;
        } else {
            IrisRenderSystem.deleteBuffers(id);
        }
    }
}
