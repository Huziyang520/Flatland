package com.huziyang.flatland.mixin;

import com.huziyang.flatland.world.FlatRuntime;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the flatness band field the world seed. The router is left untouched: terrain is cut
 * after noise generation, in NoiseBasedChunkGenerator, so non-flat biomes keep vanilla density.
 */
@Mixin(RandomState.class)
public abstract class RandomStateMixin {
    @Inject(method = "<init>(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/core/HolderGetter;J)V",
            at = @At("TAIL"), remap = false)
    private void flatland$seed(NoiseGeneratorSettings settings,
            HolderGetter<NormalNoise.NoiseParameters> noises, long seed, CallbackInfo ci) {
        if (FlatRuntime.isOverworldLike(settings)) {
            FlatRuntime.seed(seed);
        }
    }
}
