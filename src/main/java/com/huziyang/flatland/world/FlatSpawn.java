package com.huziyang.flatland.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlatSpawn {
    private static final String DATA_NAME = "flatland_spawn";
    private static final int SEARCH_RADIUS = 512;
    private static final int SEARCH_STEP = 16;
    private static final float MIN_WEIGHT = 0.9F;
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
            return;
        }
        // Worlds that already have region files are not new; their spawn belongs to the player.
        if (!isFreshWorld(server)) {
            data.applied = true;
            data.setDirty();
            LOGGER.info("Flatland spawn preference skipped: the world already has generated chunks");
            return;
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

    private static boolean isFreshWorld(MinecraftServer server) {
        Path region = server.getWorldPath(LevelResource.ROOT).resolve("region");
        if (!Files.isDirectory(region)) {
            return true;
        }
        try (Stream<Path> files = Files.list(region)) {
            return files.noneMatch(path -> path.getFileName().toString().endsWith(".mca"));
        } catch (IOException exception) {
            LOGGER.warn("Flatland spawn cannot inspect {}", region, exception);
            return false;
        }
    }

    private static BlockPos findCandidate(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BiomeSource source = generator.getBiomeSource();
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
                best = candidateAt(level, generator, source, state, 0, 0);
            } else {
                for (int x = -radius; x <= radius; x += SEARCH_STEP) {
                    best = closer(best, candidateAt(level, generator, source, state, x, -radius));
                    best = closer(best, candidateAt(level, generator, source, state, x, radius));
                }
                for (int z = -radius + SEARCH_STEP; z <= radius - SEARCH_STEP; z += SEARCH_STEP) {
                    best = closer(best, candidateAt(level, generator, source, state, -radius, z));
                    best = closer(best, candidateAt(level, generator, source, state, radius, z));
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static BlockPos candidateAt(ServerLevel level, ChunkGenerator generator, BiomeSource source,
            RandomState state, int x, int z) {
        if (FlatRuntime.bandWeight(x, z) < MIN_WEIGHT) {
            return null;
        }
        try {
            Holder<Biome> biome = source.getNoiseBiome(
                    QuartPos.fromBlock(x),
                    QuartPos.fromBlock(FlatRuntime.flatHeight()),
                    QuartPos.fromBlock(z),
                    state.sampler());
            if (!FlatRuntime.isFlatBiome(biome)) {
                return null;
            }
            int spawnY = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE, level, state);
            return new BlockPos(x, spawnY, z);
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
