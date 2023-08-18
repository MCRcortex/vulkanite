package me.cortex.vulkanite.mixin;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IVCG;
import me.cortex.vulkanite.lib.base.VContext;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CommandList.class, remap = false)
public interface MixinCommandList extends IVCG {
    default VContext getCtx() {
        return Vulkanite.INSTANCE.getCtx();
    }
}
