package com.huziyang.flatland.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
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
 * Scope-aware terrain shaping. Flatland columns are always cut to the configured height; the
 * vanilla columns next to them carry the height difference as a slope, so the border is not a
 * vertical face.
 */
public final class FlatTerrain {
    private static final float BIOME_THRESHOLD = 0.5F;
    /** Furthest a border slope may reach into the vanilla side of a border. */
    private static final int EDGE_SLOPE_BLOCKS = 32;
    private static final int MIN_SLOPE_BLOCKS = 6;
    /** Height difference below which the border needs no slope. */
    private static final int MIN_SLOPE_DROP = 3;

    private static final ThreadLocal<BorderGrid> BORDER_GRID = ThreadLocal.withInitial(BorderGrid::new);

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

    public static int targetHeight(int x, int z, int vanillaTop) {
        return targetHeight(x, z, vanillaTop, FlatRuntime.bandWeight(x, z));
    }

    private static int targetHeight(int x, int z, int vanillaTop, float weight) {
        int flatHeight = FlatRuntime.flatHeight();
        if (!FlatRuntime.isActive()) {
            return vanillaTop;
        }
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

    /**
     * Target for a vanilla column next to a Flatland border: 0 at the border, the vanilla height
     * once the slope is wide enough. Only ever lowers terrain.
     */
    private static int borderTarget(BiomeSource source, RandomState state, int x, int z, int vanillaTop) {
        int flatHeight = FlatRuntime.flatHeight();
        int drop = vanillaTop - flatHeight;
        if (drop < MIN_SLOPE_DROP) {
            return vanillaTop;
        }
        int width = Math.min(EDGE_SLOPE_BLOCKS, Math.max(MIN_SLOPE_BLOCKS, drop));
        float distance = BORDER_GRID.get().distanceBlocks(source, state, x, z);
        if (distance >= width) {
            return vanillaTop;
        }
        float t = distance / width;
        t = t * t * (3.0F - 2.0F * t);
        int target = Math.round(flatHeight + drop * t);
        return Math.max(flatHeight, Math.min(vanillaTop, target));
    }

    public static int adjustHeight(BiomeSource source, RandomState state, int x, int z, int vanillaHeight) {
        float weight = FlatRuntime.bandWeight(x, z);
        if (weight < BIOME_THRESHOLD) {
            return vanillaHeight;
        }
        if (isFlatColumn(source, state, x, z)) {
            return targetHeight(x, z, vanillaHeight, weight);
        }
        return borderTarget(source, state, x, z, vanillaHeight);
    }

    public static void shave(ChunkAccess chunk, BiomeSource source, RandomState state) {
        if (!FlatRuntime.isActive()) {
            return;
        }
        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight();
        int flatHeight = FlatRuntime.flatHeight();
        int quartY = QuartPos.fromBlock(flatHeight);
        var chunkPos = chunk.getPos();
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = chunkPos.getMinBlockX() + localX;
                int z = chunkPos.getMinBlockZ() + localZ;
                float weight = FlatRuntime.bandWeight(x, z);
                if (weight < BIOME_THRESHOLD) {
                    continue;
                }
                // Sample the source, not the chunk: some versions call buildSurface before the
                // chunk biome container is filled, which would leave every column unshaved.
                Holder<Biome> biome = source.getNoiseBiome(
                        QuartPos.fromBlock(x), quartY, QuartPos.fromBlock(z), state.sampler());
                int top = topSolid(chunk, x, z, localX, localZ, minY, maxY, heightmap, cursor);
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                if (isFlatColumn(biome, x, z)) {
                    int target = targetHeight(x, z, top, weight);
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
                } else {
                    int target = borderTarget(source, state, x, z, top);
                    if (target < top) {
                        for (int y = target + 1; y <= top; y++) {
                            cursor.set(x, y, z);
                            BlockState existing = chunk.getBlockState(cursor);
                            if (!existing.isAir() && existing.getFluidState().isEmpty()) {
                                chunk.setBlockState(cursor, Blocks.AIR.defaultBlockState(), 2);
                            }
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
        int minY = level.getMinY();
        int height = level.getHeight();
        int top = topSolid(original, minY, minY + height);
        if (top == Integer.MIN_VALUE) {
            return original;
        }
        int target;
        if (isFlatColumn(source, state, x, z)) {
            target = targetHeight(x, z, top, weight);
        } else {
            target = borderTarget(source, state, x, z, top);
        }
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

    private static int topSolid(NoiseColumn column, int minY, int maxY) {
        for (int y = maxY - 1; y >= minY; y--) {
            BlockState state = column.getBlock(y);
            if (state.isAir()) {
                continue;
            }
            return state.getFluidState().isEmpty() ? y : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Coarse grid with the distance, in blocks, from every column to the nearest Flatland cell.
     * Built per chunk from the biome source so both sides of a chunk border agree.
     */
    private static final class BorderGrid {
        private static final int CELL_BLOCKS = 4;
        private static final int CHUNK_CELLS = 16 / CELL_BLOCKS;
        private static final int MARGIN_CELLS = EDGE_SLOPE_BLOCKS / CELL_BLOCKS + 2;
        private static final int GRID = CHUNK_CELLS + 2 * MARGIN_CELLS;
        private static final float DIAGONAL = 1.4142135F;
        private static final float FAR = 4096.0F;

        private final float[] distance = new float[GRID * GRID];
        private int chunkX = Integer.MIN_VALUE;
        private int chunkZ = Integer.MIN_VALUE;

        float distanceBlocks(BiomeSource source, RandomState state, int x, int z) {
            int currentChunkX = x >> 4;
            int currentChunkZ = z >> 4;
            if (currentChunkX != chunkX || currentChunkZ != chunkZ) {
                rebuild(source, state, currentChunkX, currentChunkZ);
                chunkX = currentChunkX;
                chunkZ = currentChunkZ;
            }
            float originX = chunkX * CHUNK_CELLS - MARGIN_CELLS;
            float originZ = chunkZ * CHUNK_CELLS - MARGIN_CELLS;
            float fx = (x - CELL_BLOCKS * 0.5F) / CELL_BLOCKS - originX;
            float fz = (z - CELL_BLOCKS * 0.5F) / CELL_BLOCKS - originZ;
            int i0 = Math.max(0, Math.min(GRID - 2, (int) Math.floor(fx)));
            int j0 = Math.max(0, Math.min(GRID - 2, (int) Math.floor(fz)));
            float tx = Math.max(0.0F, Math.min(1.0F, fx - i0));
            float tz = Math.max(0.0F, Math.min(1.0F, fz - j0));
            float near = distance[i0 * GRID + j0];
            float nearX = distance[(i0 + 1) * GRID + j0];
            float far = distance[i0 * GRID + j0 + 1];
            float farX = distance[(i0 + 1) * GRID + j0 + 1];
            float low = near + (nearX - near) * tx;
            float high = far + (farX - far) * tx;
            float cells = low + (high - low) * tz;
            return Math.max(0.0F, cells * CELL_BLOCKS - CELL_BLOCKS * 0.5F);
        }

        private void rebuild(BiomeSource source, RandomState state, int currentChunkX, int currentChunkZ) {
            Climate.Sampler sampler = state.sampler();
            int originX = currentChunkX * CHUNK_CELLS - MARGIN_CELLS;
            int originZ = currentChunkZ * CHUNK_CELLS - MARGIN_CELLS;
            int quartFlat = QuartPos.fromBlock(FlatRuntime.flatHeight());
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    int x = (originX + i) * CELL_BLOCKS + CELL_BLOCKS / 2;
                    int z = (originZ + j) * CELL_BLOCKS + CELL_BLOCKS / 2;
                    Holder<Biome> biome = source.getNoiseBiome(
                            QuartPos.fromBlock(x), quartFlat, QuartPos.fromBlock(z), sampler);
                    distance[i * GRID + j] = isFlatColumn(biome, x, z) ? 0.0F : FAR;
                }
            }
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    int index = i * GRID + j;
                    float best = distance[index];
                    if (i > 0) {
                        best = Math.min(best, distance[index - GRID] + 1.0F);
                        if (j > 0) {
                            best = Math.min(best, distance[index - GRID - 1] + DIAGONAL);
                        }
                        if (j + 1 < GRID) {
                            best = Math.min(best, distance[index - GRID + 1] + DIAGONAL);
                        }
                    }
                    if (j > 0) {
                        best = Math.min(best, distance[index - 1] + 1.0F);
                    }
                    distance[index] = best;
                }
            }
            for (int i = GRID - 1; i >= 0; i--) {
                for (int j = GRID - 1; j >= 0; j--) {
                    int index = i * GRID + j;
                    float best = distance[index];
                    if (i + 1 < GRID) {
                        best = Math.min(best, distance[index + GRID] + 1.0F);
                        if (j + 1 < GRID) {
                            best = Math.min(best, distance[index + GRID + 1] + DIAGONAL);
                        }
                        if (j > 0) {
                            best = Math.min(best, distance[index + GRID - 1] + DIAGONAL);
                        }
                    }
                    if (j + 1 < GRID) {
                        best = Math.min(best, distance[index + 1] + 1.0F);
                    }
                    distance[index] = best;
                }
            }
        }
    }
}
