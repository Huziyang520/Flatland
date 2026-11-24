package com.huziyang.flatland.mixin;

import com.huziyang.flatland.world.FlatRuntime;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the flatness band field the world seed. No noise router changes are made, so
 * non-flat biomes keep vanilla density; flat columns are cut in NoiseBasedChunkGenerator.
 */
@Mixin(RandomState.class)
public abstract class RandomStateMixin {
    @Inject(
            method = "<init>(Lnet/minecraft/core/HolderGetter;JZLnet/minecraft/world/level/block/state/BlockState;ILnet/minecraft/world/level/levelgen/NoiseRouter;)V",
            at = @At("TAIL"), remap = false)
    private void flatland$seed(HolderGetter<NormalNoise> noises, long seed,
            boolean something, BlockState defaultBlock, int seaLevel, NoiseRouter router, CallbackInfo ci) {
        if (FlatRuntime.isOverworldLike(defaultBlock, seaLevel)) {
            FlatRuntime.seed(seed);
        }
    }
}
