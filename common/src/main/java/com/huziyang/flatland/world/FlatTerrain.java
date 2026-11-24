package com.huziyang.flatland.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

public final class FlatTerrain {
    private static final float BIOME_THRESHOLD = 0.5F;

    private FlatTerrain() {
    }

    public static boolean isFlatColumn(Holder<Biome> biome, int x, int z) {
        return FlatRuntime.isActive() && FlatRuntime.isFlatBiome(biome);
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

    public static void shave(ChunkAccess chunk, BiomeManager biomeManager) {
        if (!FlatRuntime.isActive()) {
            return;
        }
        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight();
        int flatHeight = FlatRuntime.flatHeight();
        var pos = chunk.getPos();
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = pos.getMinBlockX() + localX;
                int z = pos.getMinBlockZ() + localZ;
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
                if (target == top && top == flatHeight) {
                    setSurface(chunk, cursor, x, target, z, biome);
                    continue;
                }
                if (target > top) {
                    for (int y = top + 1; y < target; y++) {
                        cursor.set(x, y, z);
                        chunk.setBlockState(cursor, Blocks.DIRT.defaultBlockState(), 2);
                    }
                } else {
                    for (int y = target + 1; y <= top; y++) {
                        cursor.set(x, y, z);
                        BlockState state = chunk.getBlockState(cursor);
                        if (!state.isAir() && state.getFluidState().isEmpty()) {
                            chunk.setBlockState(cursor, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
                setSurface(chunk, cursor, x, target, z, biome);
            }
        }
    }

    private static boolean setSurface(ChunkAccess chunk, BlockPos.MutableBlockPos cursor,
            int x, int y, int z, Holder<Biome> biome) {
        if (y <= chunk.getMinY() || y >= chunk.getMinY() + chunk.getHeight()) {
            return false;
        }
        BlockState surface = surfaceState(biome);
        cursor.set(x, y, z);
        BlockState state = chunk.getBlockState(cursor);
        if (state == surface) {
            return false;
        }
        if (y > chunk.getMinY()) {
            cursor.set(x, y - 1, z);
            BlockState below = chunk.getBlockState(cursor);
            if (below.isAir()) {
                chunk.setBlockState(cursor, Blocks.DIRT.defaultBlockState(), 2);
            }
        }
        cursor.set(x, y, z);
        chunk.setBlockState(cursor, surface, 2);
        return true;
    }

    private static BlockState surfaceState(Holder<Biome> biome) {
        if (FlatRuntime.isSandyBiome(biome)) {
            return Blocks.SAND.defaultBlockState();
        }
        if (biome.unwrapKey().map(key -> key.equals(FlatRuntime.FLAT_SNOWY)).orElse(false)) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    public static void paintDesertSurface(ChunkAccess chunk, BiomeManager biomeManager) {
        shave(chunk, biomeManager);
    }

    public static NoiseColumn adjustColumn(NoiseColumn original, LevelHeightAccessor level) {
        return original;
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
}
