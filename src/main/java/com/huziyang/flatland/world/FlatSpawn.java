package com.huziyang.flatland.world;

import com.huziyang.flatland.Flatland;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelData;

public final class FlatSpawn {
    private static final SavedDataType<Data> DATA = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Flatland.MOD_ID, "spawn"),
            Data::new,
            Data.CODEC,
            DataFixTypes.LEVEL);

    private FlatSpawn() {
    }

    public static void applyOnFirstLoad(MinecraftServer server) {
        if (!FlatRuntime.isLoaded() || !FlatRuntime.spawnPrefer()) {
            return;
        }
        ServerLevel level = server.overworld();
        Data data = level.getDataStorage().computeIfAbsent(DATA);
        if (data.applied) {
            return;
        }
        data.applied = true;
        data.setDirty();
        BlockPos candidate = findCandidate(level);
        if (candidate != null) {
            level.setRespawnData(LevelData.RespawnData.of(level.dimension(), candidate, 0.0F, 0.0F));
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
        private static final Codec<Data> CODEC = Codec.BOOL.xmap(
                applied -> {
                    Data data = new Data();
                    data.applied = applied;
                    return data;
                },
                data -> data.applied);
        private boolean applied;

        private Data() {
        }
    }
}
