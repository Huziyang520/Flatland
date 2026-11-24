package com.huziyang.flatland.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlatSpawn {
    private static final String DATA_NAME = "flatland_spawn";
    private static final int SEARCH_RADIUS = 512;
    private static final int SEARCH_STEP = 16;
    private static final float MIN_WEIGHT = 0.5F;
    private static final Logger LOGGER = LoggerFactory.getLogger("Flatland");

    private FlatSpawn() {
    }

    public static void applyOnFirstLoad(MinecraftServer server) {
        if (!FlatRuntime.isActive() || !FlatRuntime.spawnPrefer()) {
            return;
        }
        ServerLevel level = server.overworld();
        Data data = level.getDataStorage().computeIfAbsent(Data.FACTORY, DATA_NAME);
        if (data.applied) {
            if (isFlatSpawn(level, level.getSharedSpawnPos())) {
                return;
            }
            LOGGER.info("Flatland spawn marker is stale at {}; retrying", level.getSharedSpawnPos());
            data.applied = false;
            data.setDirty();
        }
        FlatRuntime.seed(level.getSeed());
        BlockPos candidate = findCandidate(level);
        if (candidate == null) {
            LOGGER.warn("Flatland spawn preference found no flatland within {} blocks; keeping {}",
                    SEARCH_RADIUS, level.getSharedSpawnPos());
            return;
        }
        data.applied = true;
        data.setDirty();
        level.setDefaultSpawnPos(candidate, 0.0F);
        LOGGER.info("Flatland spawn moved to {}", candidate);
    }

    private static boolean isFlatSpawn(ServerLevel level, BlockPos pos) {
        try {
            return FlatRuntime.isFlatBiome(level.getBiome(pos));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static BlockPos findCandidate(ServerLevel level) {
        int y = FlatRuntime.flatHeight();
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        RandomState state;
        try {
            state = level.getChunkSource().randomState();
        } catch (RuntimeException exception) {
            LOGGER.warn("Flatland spawn cannot read the world random state", exception);
            return null;
        }

        for (int radius = 0; radius <= SEARCH_RADIUS; radius += SEARCH_STEP) {
            BlockPos best = null;
            if (radius == 0) {
                best = candidateAt(source, state, 0, 0, y);
            } else {
                for (int x = -radius; x <= radius; x += SEARCH_STEP) {
                    best = closer(best, candidateAt(source, state, x, -radius, y));
                    best = closer(best, candidateAt(source, state, x, radius, y));
                }
                for (int z = -radius + SEARCH_STEP; z <= radius - SEARCH_STEP; z += SEARCH_STEP) {
                    best = closer(best, candidateAt(source, state, -radius, z, y));
                    best = closer(best, candidateAt(source, state, radius, z, y));
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static BlockPos candidateAt(BiomeSource source, RandomState state, int x, int z, int y) {
        if (FlatRuntime.bandWeight(x, z) < MIN_WEIGHT) {
            return null;
        }
        try {
            Holder<Biome> biome = source.getNoiseBiome(
                    QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), state.sampler());
            return FlatRuntime.isFlatBiome(biome) ? new BlockPos(x, y + 1, z) : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static BlockPos closer(BlockPos best, BlockPos candidate) {
        if (candidate == null) {
            return best;
        }
        if (best == null) {
            return candidate;
        }
        double candidateDistance = (double) candidate.getX() * candidate.getX()
                + (double) candidate.getZ() * candidate.getZ();
        double bestDistance = (double) best.getX() * best.getX()
                + (double) best.getZ() * best.getZ();
        return candidateDistance < bestDistance ? candidate : best;
    }

    private static final class Data extends SavedData {
        private static final SavedData.Factory<Data> FACTORY =
                new SavedData.Factory<>(Data::new, Data::load, DataFixTypes.LEVEL);
        private boolean applied;

        private Data() {
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            data.applied = tag.getBoolean("applied");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("applied", this.applied);
            return tag;
        }
    }
}
