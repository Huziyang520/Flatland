package com.huziyang.flatland.mixin;

import com.huziyang.flatland.world.FlatTerrain;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @Inject(
            method = "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Ljava/util/Set;Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;)V",
            at = @At("TAIL"), remap = false)
    private void flatland$finishSurface(ChunkAccess chunk, NoiseChunk noiseChunk, RandomState state,
            BiomeManager biomeManager, Set<Holder<Biome>> biomes, MaterialRule rule, CallbackInfo ci) {
        FlatTerrain.shave(chunk, biomeManager);
    }
}
