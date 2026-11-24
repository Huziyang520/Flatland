package com.huziyang.flatland.mixin;

import com.huziyang.flatland.world.FlatBiomeResolver;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Wraps the biome resolver used when a chunk's biomes are filled, so the flatland band
 * still has block coordinates available even though 26.3 no longer exposes a coordinate
 * overload on MultiNoiseBiomeSource.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Redirect(
            method = "doCreateBiomes(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;)V"),
            remap = false)
    private void flatland$fillBiomes(ChunkAccess chunk, BiomeResolver resolver) {
        chunk.fillBiomesFromNoise(new FlatBiomeResolver(resolver));
    }
}
