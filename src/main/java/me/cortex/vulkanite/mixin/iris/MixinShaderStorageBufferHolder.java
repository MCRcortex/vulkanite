package me.cortex.vulkanite.mixin.iris;

import com.mojang.blaze3d.platform.GlStateManager;
import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IVGBuffer;
import me.cortex.vulkanite.lib.memory.VGBuffer;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.buffer.ShaderStorageBuffer;
import net.coderbot.iris.gl.buffer.ShaderStorageBufferHolder;
import net.coderbot.iris.gl.buffer.ShaderStorageInfo;
import org.lwjgl.opengl.GL43C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;

@Mixin(value = ShaderStorageBufferHolder.class, remap = false)
public class MixinShaderStorageBufferHolder {
    @Unique private ShaderStorageInfo storageInfo;

    @Redirect(method = "lambda$new$0", at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/gl/buffer/ShaderStorageInfo;relative()Z"))
    private boolean alwaysReturnTrue(ShaderStorageInfo instance) {
        this.storageInfo = instance;
        return true;
    }

    private static VGBuffer alloc(int size) {
        return Vulkanite.INSTANCE.getCtx().memory.createSharedBuffer(size, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    }

    @Redirect(method = "lambda$new$0", at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/gl/buffer/ShaderStorageBuffer;resizeIfRelative(II)V"))
    private void redirectSizeAllocation(ShaderStorageBuffer instance, int width, int height) {
        if (storageInfo.relative()) {
            instance.resizeIfRelative(width, height);
        } else {
            ((IVGBuffer)instance).setBuffer(alloc(storageInfo.size()));
            GlStateManager._glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, instance.getId());
            IrisRenderSystem.clearBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, GL43C.GL_R8, 0, storageInfo.size(), GL43C.GL_RED, GL43C.GL_BYTE, new int[] {0});
            IrisRenderSystem.bindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, instance.getIndex(), instance.getId());
        }
    }

}
