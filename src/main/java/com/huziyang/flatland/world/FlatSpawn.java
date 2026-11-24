package com.huziyang.flatland.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
        Data data = level.getDataStorage().computeIfAbsent(Data::load, Data::new, DATA_NAME);
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

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x += SEARCH_STEP) {
            for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z += SEARCH_STEP) {
                if (FlatRuntime.bandWeight(x, z) < MIN_WEIGHT) {
                    continue;
                }
                Holder<Biome> biome;
                try {
                    biome = source.getNoiseBiome(
                            QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), state.sampler());
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (!FlatRuntime.isFlatBiome(biome)) {
                    continue;
                }
                double distance = (double) x * x + (double) z * z;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new BlockPos(x, y + 1, z);
                }
            }
        }
        return best;
    }

    private static final class Data extends SavedData {
        private boolean applied;

        private Data() {
        }

        private static Data load(CompoundTag tag) {
            Data data = new Data();
            data.applied = tag.getBoolean("applied");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("applied", this.applied);
            return tag;
        }
    }
}
