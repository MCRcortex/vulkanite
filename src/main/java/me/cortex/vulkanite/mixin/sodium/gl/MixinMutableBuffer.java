package me.cortex.vulkanite.mixin.sodium.gl;

import me.cortex.vulkanite.compat.IVGBuffer;
import me.cortex.vulkanite.lib.memory.VGBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import static org.lwjgl.opengl.GL15C.glDeleteBuffers;

@Mixin(value = GlMutableBuffer.class, remap = false)
public class MixinMutableBuffer extends GlBuffer implements IVGBuffer {
    @Unique private VGBuffer vkBuffer;

    public VGBuffer getBuffer() {
        return vkBuffer;
    }

    public void setBuffer(VGBuffer buffer) {
        if (vkBuffer != null && buffer != null) {
            throw new IllegalStateException("Override buffer not null");
        }
        this.vkBuffer = buffer;
        if (buffer != null) {
            glDeleteBuffers(handle());
            setHandle(vkBuffer.glId);
        }
    }
}
