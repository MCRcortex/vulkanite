package me.cortex.vulkanite.mixin.iris;

import com.google.common.collect.ImmutableSet;
import net.coderbot.iris.shaderpack.PackRenderTargetDirectives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

@Mixin(value = PackRenderTargetDirectives.class, remap = false)
public class MixinPackRenderTargetDirectives {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableSet$Builder;build()Lcom/google/common/collect/ImmutableSet;"))
    private static ImmutableSet<Integer> redirectBuild(ImmutableSet.Builder<Integer> instance) {
        ImmutableSet.Builder<Integer> fake = new ImmutableSet.Builder<>();
        for (int i = 0; i < 10; i++) {
            fake.add(i);
        }
        return fake.build();
    }
}
