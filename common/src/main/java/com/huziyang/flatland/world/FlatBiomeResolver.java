package com.huziyang.flatland.world;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;

public final class FlatBiomeResolver implements BiomeResolver {
    private final BiomeResolver delegate;

    public FlatBiomeResolver(BiomeResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        Holder<Biome> original = delegate.getNoiseBiome(x, y, z);
        Holder<Biome> replacement = FlatRuntime.remap(original, QuartPos.toBlock(x), QuartPos.toBlock(z));
        return replacement != null ? replacement : original;
    }
}
