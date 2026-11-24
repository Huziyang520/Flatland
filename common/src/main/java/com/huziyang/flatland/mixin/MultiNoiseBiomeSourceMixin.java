package com.huziyang.flatland.mixin;

import com.huziyang.flatland.world.FlatBiomeResolver;
import com.huziyang.flatland.world.FlatRuntime;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
    /**
     * 26.3 dropped the coordinate overload, so every consumer (including /locate biome)
     * receives a resolver whose getNoiseBiome only has quart coordinates. Wrap the
     * resolver at the source instead of patching the biome-fill call site.
     */
    @Inject(method = "createResolver(Lnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/world/level/biome/BiomeResolver;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void flatland$wrapResolver(Climate.Sampler sampler, CallbackInfoReturnable<BiomeResolver> cir) {
        flatland$wrap(cir);
    }

    @Inject(method = "createResolverForChunk(Lnet/minecraft/world/level/biome/Climate$Sampler;IIIIII)Lnet/minecraft/world/level/biome/BiomeResolver;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void flatland$wrapChunkResolver(Climate.Sampler sampler, int minQuartX, int minQuartY,
            int minQuartZ, int maxQuartX, int maxQuartY, int maxQuartZ,
            CallbackInfoReturnable<BiomeResolver> cir) {
        flatland$wrap(cir);
    }

    private static void flatland$wrap(CallbackInfoReturnable<BiomeResolver> cir) {
        BiomeResolver original = cir.getReturnValue();
        if (original != null && !(original instanceof FlatBiomeResolver)) {
            cir.setReturnValue(new FlatBiomeResolver(original));
        }
    }

    @Inject(method = "collectPossibleBiomes()Ljava/util/stream/Stream;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void flatland$possibleBiomes(CallbackInfoReturnable<Stream<Holder<Biome>>> cir) {
        cir.setReturnValue(Stream.concat(cir.getReturnValue(), FlatRuntime.possibleBiomes()).distinct());
    }
}
