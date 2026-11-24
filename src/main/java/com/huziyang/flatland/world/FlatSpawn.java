package com.huziyang.flatland.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;

public final class FlatSpawn {
    private static final String DATA_NAME = "flatland_spawn";

    private FlatSpawn() {
    }

    public static void applyOnFirstLoad(MinecraftServer server) {
        if (!FlatRuntime.isLoaded() || !FlatRuntime.spawnPrefer()) {
            return;
        }
        ServerLevel level = server.overworld();
        Data data = level.getDataStorage().computeIfAbsent(Data::load, Data::new, DATA_NAME);
        if (data.applied) {
            return;
        }
        data.applied = true;
        data.setDirty();
        BlockPos candidate = findCandidate(level);
        if (candidate != null) {
            level.setDefaultSpawnPos(candidate, 0.0F);
        }
    }

    private static BlockPos findCandidate(ServerLevel level) {
        int y = FlatRuntime.flatHeight();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -160; x <= 160; x += 16) {
            for (int z = -160; z <= 160; z += 16) {
                if (FlatBand.weight(x, z) * FlatRuntime.strength() < 0.9F) {
                    continue;
                }
                Holder<Biome> biome;
                try {
                    biome = level.getBiome(new BlockPos(x, y, z));
                } catch (RuntimeException ignored) {
                    // Outside the already generated spawn area: skip instead of forcing chunk generation.
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
