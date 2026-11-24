package com.huziyang.flatland.world;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Scope-aware terrain shaping. Only columns whose final biome is a Flatland biome are cut,
 * so vanilla plains outside the replacement band keep their original terrain.
 */
public final class FlatTerrain {
    private static final float BIOME_THRESHOLD = 0.5F;

    private FlatTerrain() {
    }

    public static boolean isFlatColumn(Holder<Biome> biome, int x, int z) {
        // The biome remap is already band-gated; checking the band again at block resolution
        // would make 1-3 block strips at quart boundaries disagree with the generated biome.
        return FlatRuntime.isActive() && FlatRuntime.isFlatBiome(biome);
    }

    public static boolean isFlatColumn(BiomeSource source, RandomState state, int x, int z) {
        Holder<Biome> biome = source.getNoiseBiome(
                QuartPos.fromBlock(x),
                QuartPos.fromBlock(FlatRuntime.flatHeight()),
                QuartPos.fromBlock(z),
                state.sampler());
        return isFlatColumn(biome, x, z);
    }

    public static int targetHeight(int x, int z, int vanillaTop) {
        int flatHeight = FlatRuntime.flatHeight();
        if (!FlatRuntime.isActive()) {
            return vanillaTop;
        }
        float weight = FlatRuntime.bandWeight(x, z);
        if (weight < BIOME_THRESHOLD) {
            return vanillaTop;
        }
        float t = (weight - BIOME_THRESHOLD) / (1.0F - BIOME_THRESHOLD);
        t = t * t * (3.0F - 2.0F * t) * FlatRuntime.strength();
        int target = Math.round(vanillaTop + (flatHeight - vanillaTop) * t);
        int low = Math.min(vanillaTop, flatHeight);
        int high = Math.max(vanillaTop, flatHeight);
        return Math.max(low, Math.min(high, target));
    }

    public static int adjustHeight(BiomeSource source, RandomState state, int x, int z, int vanillaHeight) {
        if (!isFlatColumn(source, state, x, z)) {
            return vanillaHeight;
        }
        return targetHeight(x, z, vanillaHeight);
    }

    public static void shave(ChunkAccess chunk, BiomeManager biomeManager) {
        if (!FlatRuntime.isActive()) {
            return;
        }
        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight();
        var pos = chunk.getPos();
        boolean changed = false;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = pos.getMinBlockX() + localX;
                int z = pos.getMinBlockZ() + localZ;
                Holder<Biome> biome = biomeManager.getBiome(
                        new BlockPos(x, FlatRuntime.flatHeight(), z));
                if (!isFlatColumn(biome, x, z)) {
                    continue;
                }
                int top = topSolid(chunk, x, z, minY, maxY);
                if (top < 0) {
                    continue;
                }
                int target = targetHeight(x, z, top);
                if (target < top) {
                    for (int y = target + 1; y <= top; y++) {
                        BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
                        if (!state.isAir() && state.getFluidState().isEmpty()) {
                            chunk.setBlockState(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                            changed = true;
                        }
                    }
                } else if (target > top) {
                    for (int y = top + 1; y <= target; y++) {
                        chunk.setBlockState(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(), 2);
                        changed = true;
                    }
                }
                if (target >= minY && target < maxY) {
                    BlockState surface = surfaceFor(biome);
                    if (!chunk.getBlockState(new BlockPos(x, target, z)).equals(surface)) {
                        chunk.setBlockState(new BlockPos(x, target, z), surface, 2);
                        changed = true;
                    }
                    if (target > minY) {
                        BlockPos below = new BlockPos(x, target - 1, z);
                        if (chunk.getBlockState(below).isAir()) {
                            chunk.setBlockState(below, Blocks.DIRT.defaultBlockState(), 2);
                            changed = true;
                        }
                    }
                }
            }
        }

        if (changed) {
            Heightmap.primeHeightmaps(chunk,
                    EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
        }
    }

    public static NoiseColumn adjustColumn(BiomeSource source, RandomState state, int x, int z,
            NoiseColumn original, LevelHeightAccessor level) {
        int minY = level.getMinY();
        int height = level.getHeight();
        int top = topSolid(original, minY, minY + height);
        if (!isFlatColumn(source, state, x, z)) {
            return original;
        }
        int target = targetHeight(x, z, top);
        if (target == top) {
            return original;
        }

        BlockState[] column = new BlockState[height];
        for (int i = 0; i < height; i++) {
            int y = minY + i;
            BlockState blockState = original.getBlock(y);
            if (y > target && !blockState.isAir() && blockState.getFluidState().isEmpty()) {
                column[i] = Blocks.AIR.defaultBlockState();
            } else if (target > top && y > top && y <= target) {
                column[i] = Blocks.DIRT.defaultBlockState();
            } else {
                column[i] = blockState;
            }
        }
        return new NoiseColumn(minY, column);
    }

    private static BlockState surfaceFor(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> {
            String path = key.identifier().getPath();
            if ("flat_desert".equals(path)) {
                return Blocks.SAND.defaultBlockState();
            }
            if ("flat_snowy_plains".equals(path)) {
                return Blocks.SNOW_BLOCK.defaultBlockState();
            }
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }).orElse(Blocks.GRASS_BLOCK.defaultBlockState());
    }

    private static int topSolid(ChunkAccess chunk, int x, int z, int minY, int maxY) {
        for (int y = maxY - 1; y >= minY; y--) {
            BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
            if (state.isAir()) {
                continue;
            }
            return state.getFluidState().isEmpty() ? y : -1;
        }
        return -1;
    }

    private static int topSolid(BlockColumn column, int minY, int maxY) {
        for (int y = maxY - 1; y >= minY; y--) {
            BlockState state = column.getBlock(y);
            if (state.isAir()) {
                continue;
            }
            return state.getFluidState().isEmpty() ? y : -1;
        }
        return -1;
    }
}
