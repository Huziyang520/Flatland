package com.huziyang.flatland.world;

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
        if (FlatRuntime.bandWeight(x, z) < BIOME_THRESHOLD) {
            return vanillaHeight;
        }
        if (!isFlatColumn(source, state, x, z)) {
            return vanillaHeight;
        }
        return targetHeight(x, z, vanillaHeight);
    }

    public static void shave(ChunkAccess chunk, BiomeManager biomeManager) {
        if (!FlatRuntime.isActive()) {
            return;
        }
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int flatHeight = FlatRuntime.flatHeight();
        var chunkPos = chunk.getPos();
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = chunkPos.getMinBlockX() + localX;
                int z = chunkPos.getMinBlockZ() + localZ;
                if (FlatRuntime.bandWeight(x, z) < BIOME_THRESHOLD) {
                    continue;
                }
                cursor.set(x, flatHeight, z);
                Holder<Biome> biome = biomeManager.getBiome(cursor);
                if (!isFlatColumn(biome, x, z)) {
                    continue;
                }
                int top = topSolid(chunk, x, z, localX, localZ, minY, maxY, heightmap, cursor);
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                int target = targetHeight(x, z, top);
                if (target < top) {
                    for (int y = target + 1; y <= top; y++) {
                        cursor.set(x, y, z);
                        BlockState state = chunk.getBlockState(cursor);
                        if (!state.isAir() && state.getFluidState().isEmpty()) {
                            chunk.setBlockState(cursor, Blocks.AIR.defaultBlockState(), false);
                        }
                    }
                } else if (target > top) {
                    for (int y = top + 1; y <= target; y++) {
                        cursor.set(x, y, z);
                        chunk.setBlockState(cursor, Blocks.DIRT.defaultBlockState(), false);
                    }
                }
                if (target >= minY && target < maxY) {
                    BlockState surface = surfaceFor(biome);
                    cursor.set(x, target, z);
                    if (!chunk.getBlockState(cursor).equals(surface)) {
                        chunk.setBlockState(cursor, surface, false);
                    }
                    if (target > minY) {
                        cursor.set(x, target - 1, z);
                        if (chunk.getBlockState(cursor).isAir()) {
                            chunk.setBlockState(cursor, Blocks.DIRT.defaultBlockState(), false);
                        }
                    }
                }
            }
        }
    }

    public static NoiseColumn adjustColumn(BiomeSource source, RandomState state, int x, int z,
            NoiseColumn original, LevelHeightAccessor level) {
        if (FlatRuntime.bandWeight(x, z) < BIOME_THRESHOLD) {
            return original;
        }
        if (!isFlatColumn(source, state, x, z)) {
            return original;
        }
        int minY = level.getMinBuildHeight();
        int height = level.getHeight();
        int top = topSolid(original, minY, minY + height);
        if (top == Integer.MIN_VALUE) {
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
            String path = key.location().getPath();
            if ("flat_desert".equals(path)) {
                return Blocks.SAND.defaultBlockState();
            }
            if ("flat_snowy_plains".equals(path)) {
                return Blocks.SNOW_BLOCK.defaultBlockState();
            }
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }).orElse(Blocks.GRASS_BLOCK.defaultBlockState());
    }

    private static int topSolid(ChunkAccess chunk, int x, int z, int localX, int localZ,
            int minY, int maxY, Heightmap heightmap, BlockPos.MutableBlockPos cursor) {
        int heightmapTop = heightmap.getFirstAvailable(localX, localZ) - 1;
        if (heightmapTop >= minY && heightmapTop < maxY) {
            cursor.set(x, heightmapTop, z);
            BlockState state = chunk.getBlockState(cursor);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return heightmapTop;
            }
        }
        for (int y = maxY - 1; y >= minY; y--) {
            cursor.set(x, y, z);
            BlockState state = chunk.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            return state.getFluidState().isEmpty() ? y : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static int topSolid(BlockColumn column, int minY, int maxY) {
        for (int y = maxY - 1; y >= minY; y--) {
            BlockState state = column.getBlock(y);
            if (state.isAir()) {
                continue;
            }
            return state.getFluidState().isEmpty() ? y : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }
}
