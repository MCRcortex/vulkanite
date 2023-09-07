package me.cortex.vulkanite.mixin.iris;

import me.cortex.vulkanite.compat.IGetRaytracingSource;
import me.cortex.vulkanite.compat.RaytracingShaderSource;
import net.coderbot.iris.shaderpack.ProgramSet;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.shaderpack.ShaderPack;
import net.coderbot.iris.shaderpack.ShaderProperties;
import net.coderbot.iris.shaderpack.include.AbsolutePackPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Mixin(value = ProgramSet.class, remap = false)
public abstract class MixinProgramSet implements IGetRaytracingSource {
    @Unique private RaytracingShaderSource[] sources;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void injectRTShaders(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider,
                                 ShaderProperties shaderProperties, ShaderPack pack, CallbackInfo ci) {
        List<RaytracingShaderSource> sourceList = new ArrayList<>();
        int passId = 0;
        while (true) {
            int pass = passId++;
            var gen = sourceProvider.apply(directory.resolve("ray"+pass+".rgen"));
            if (gen == null)
                break;
            List<String> missSources = new ArrayList<>();
            int missId = 0;
            while (true) {
                var miss = sourceProvider.apply(directory.resolve("ray"+pass+"_"+(missId++)+".rmiss"));
                if (miss == null)
                    break;
                missSources.add(miss);
            }
            List<RaytracingShaderSource.RayHitSource> hitSources = new ArrayList<>();
            int hitId = 0;
            while (true) {
                int hit = hitId++;
                var close = sourceProvider.apply(directory.resolve("ray"+pass+"_"+hit+".rchit"));
                var any = sourceProvider.apply(directory.resolve("ray"+pass+"_"+hit+".rahit"));
                var intersect = sourceProvider.apply(directory.resolve("ray"+pass+"_"+hit+".rint"));
                if (close == null && any == null && intersect == null)
                    break;
                hitSources.add(new RaytracingShaderSource.RayHitSource(close, any, intersect));
            }
            if (missSources.isEmpty()) {
                throw new IllegalStateException("No miss shaders for pass " + pass);
            }
            if (hitSources.isEmpty()) {
                throw new IllegalStateException("No hit shaders for pass " + pass);
            }
            sourceList.add(new RaytracingShaderSource("raypass_"+pass,
                    gen,
                    missSources.toArray(new String[0]),
                    hitSources.toArray(new RaytracingShaderSource.RayHitSource[0])));
        }
        if (!sourceList.isEmpty()) {
            sources = sourceList.toArray(new RaytracingShaderSource[0]);
        }
    }

    @Override
    public RaytracingShaderSource[] getRaytracingSource() {
        return sources;
    }
}
