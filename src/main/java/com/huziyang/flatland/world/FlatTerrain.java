package com.huziyang.flatland.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
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
    /** Distance over which the cut fades out, so a biome border becomes a slope, not a wall. */
    private static final int EDGE_FADE_BLOCKS = 12;
    private static final float FADE_PROBES = 8.0F;

    private FlatTerrain() {
    }

    public static boolean isFlatColumn(Holder<Biome> biome, int x, int z) {
        // The biome remap is already band-gated; checking the band again at block resolution
        // would make 1-3 block strips at quart boundaries disagree with the generated biome.
        return FlatRuntime.isActive() && FlatRuntime.isFlatBiome(biome);
    }

    public static boolean isFlatColumn(BiomeSource source, RandomState state, int x, int z) {
        return isFlatColumn(source.getNoiseBiome(
                QuartPos.fromBlock(x),
                QuartPos.fromBlock(FlatRuntime.flatHeight()),
                QuartPos.fromBlock(z),
                state.sampler()), x, z);
    }

    /**
     * 0..1 cut strength for a column already known to be flat: it stays at 1 only while the
     * eight probes {@link #EDGE_FADE_BLOCKS} away are flat too. Near a biome border the cut
     * weakens, so the surface climbs back to vanilla height instead of ending in a sheer face.
     */
    public static float edgeFade(BiomeSource source, RandomState state, int x, int z) {
        Climate.Sampler sampler = state.sampler();
        int flat = 1;
        flat += flatProbe(source, sampler, x + EDGE_FADE_BLOCKS, z);
        flat += flatProbe(source, sampler, x - EDGE_FADE_BLOCKS, z);
        flat += flatProbe(source, sampler, x, z + EDGE_FADE_BLOCKS);
        flat += flatProbe(source, sampler, x, z - EDGE_FADE_BLOCKS);
        flat += flatProbe(source, sampler, x + EDGE_FADE_BLOCKS, z + EDGE_FADE_BLOCKS);
        flat += flatProbe(source, sampler, x - EDGE_FADE_BLOCKS, z - EDGE_FADE_BLOCKS);
        flat += flatProbe(source, sampler, x + EDGE_FADE_BLOCKS, z - EDGE_FADE_BLOCKS);
        flat += flatProbe(source, sampler, x - EDGE_FADE_BLOCKS, z + EDGE_FADE_BLOCKS);
        return flat / (FADE_PROBES + 1.0F);
    }

    private static int flatProbe(BiomeSource source, Climate.Sampler sampler, int x, int z) {
        Holder<Biome> biome = source.getNoiseBiome(
                QuartPos.fromBlock(x),
                QuartPos.fromBlock(FlatRuntime.flatHeight()),
                QuartPos.fromBlock(z),
                sampler);
        return isFlatColumn(biome, x, z) ? 1 : 0;
    }

    public static int targetHeight(int x, int z, int vanillaTop) {
        return targetHeight(x, z, vanillaTop, FlatRuntime.bandWeight(x, z), 1.0F);
    }

    private static int targetHeight(int x, int z, int vanillaTop, float weight, float fade) {
        int flatHeight = FlatRuntime.flatHeight();
        if (!FlatRuntime.isActive()) {
            return vanillaTop;
        }
        if (weight < BIOME_THRESHOLD) {
            return vanillaTop;
        }
        float t = (weight - BIOME_THRESHOLD) / (1.0F - BIOME_THRESHOLD);
        t = t * t * (3.0F - 2.0F * t) * FlatRuntime.strength() * fade;
        int target = Math.round(vanillaTop + (flatHeight - vanillaTop) * t);
        int low = Math.min(vanillaTop, flatHeight);
        int high = Math.max(vanillaTop, flatHeight);
        return Math.max(low, Math.min(high, target));
    }

    public static int adjustHeight(BiomeSource source, RandomState state, int x, int z, int vanillaHeight) {
        float weight = FlatRuntime.bandWeight(x, z);
        if (weight < BIOME_THRESHOLD) {
            return vanillaHeight;
        }
        if (!isFlatColumn(source, state, x, z)) {
            return vanillaHeight;
        }
        return targetHeight(x, z, vanillaHeight, weight, edgeFade(source, state, x, z));
    }

    public static void shave(ChunkAccess chunk, BiomeSource source, RandomState state) {
        if (!FlatRuntime.isActive()) {
            return;
        }
        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight();
        int flatHeight = FlatRuntime.flatHeight();
        int quartY = QuartPos.fromBlock(flatHeight);
        var pos = chunk.getPos();
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int fadeCellX = Integer.MIN_VALUE;
        int fadeCellZ = Integer.MIN_VALUE;
        float fade = 1.0F;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = pos.getMinBlockX() + localX;
                int z = pos.getMinBlockZ() + localZ;
                float weight = FlatRuntime.bandWeight(x, z);
                if (weight < BIOME_THRESHOLD) {
                    continue;
                }
                // Read the biome already stored in the chunk: it is the remapped one and avoids
                // the eight fiddled samples of BiomeManager#getBiome on every column.
                Holder<Biome> biome = chunk.getNoiseBiome(QuartPos.fromBlock(x), quartY, QuartPos.fromBlock(z));
                if (!isFlatColumn(biome, x, z)) {
                    continue;
                }
                int cellX = QuartPos.fromBlock(x);
                int cellZ = QuartPos.fromBlock(z);
                if (cellX != fadeCellX || cellZ != fadeCellZ) {
                    fade = edgeFade(source, state, x, z);
                    fadeCellX = cellX;
                    fadeCellZ = cellZ;
                }
                int top = topSolid(chunk, x, z, localX, localZ, minY, maxY, heightmap, cursor);
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                int target = targetHeight(x, z, top, weight, fade);
                if (target < top) {
                    for (int y = target + 1; y <= top; y++) {
                        cursor.set(x, y, z);
                        BlockState existing = chunk.getBlockState(cursor);
                        if (!existing.isAir() && existing.getFluidState().isEmpty()) {
                            chunk.setBlockState(cursor, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                } else if (target > top) {
                    for (int y = top + 1; y <= target; y++) {
                        cursor.set(x, y, z);
                        chunk.setBlockState(cursor, Blocks.DIRT.defaultBlockState(), 2);
                    }
                }
                if (target >= minY && target < maxY) {
                    BlockState surface = surfaceFor(biome);
                    cursor.set(x, target, z);
                    if (!chunk.getBlockState(cursor).equals(surface)) {
                        chunk.setBlockState(cursor, surface, 2);
                    }
                    if (target > minY) {
                        cursor.set(x, target - 1, z);
                        if (chunk.getBlockState(cursor).isAir()) {
                            chunk.setBlockState(cursor, Blocks.DIRT.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    public static NoiseColumn adjustColumn(BiomeSource source, RandomState state, int x, int z,
            NoiseColumn original, LevelHeightAccessor level) {
        float weight = FlatRuntime.bandWeight(x, z);
        if (weight < BIOME_THRESHOLD) {
            return original;
        }
        if (!isFlatColumn(source, state, x, z)) {
            return original;
        }
        int minY = level.getMinY();
        int height = level.getHeight();
        int top = topSolid(original, minY, minY + height);
        if (top == Integer.MIN_VALUE) {
            return original;
        }
        int target = targetHeight(x, z, top, weight, edgeFade(source, state, x, z));
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
        if (biome.is(FlatRuntime.FLAT_SANDY)) {
            return Blocks.SAND.defaultBlockState();
        }
        if (biome.is(FlatRuntime.FLAT_SNOWY)) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }
        return Blocks.GRASS_BLOCK.defaultBlockState();
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
