package com.huziyang.flatland.world;

import com.huziyang.flatland.Flatland;
import com.huziyang.flatland.config.FlatlandConfig;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlatRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("Flatland");

    public static final ResourceKey<Biome> FLAT_PLAINS = key("flat_plains");
    public static final ResourceKey<Biome> FLAT_SNOWY = key("flat_snowy_plains");
    public static final ResourceKey<Biome> FLAT_SANDY = key("flat_desert");

    private static volatile boolean loaded;
    private static volatile boolean active;
    private static volatile int flatHeight = 62;
    private static volatile float strength = 1.0F;
    private static volatile String variantMode = "AUTO";
    private static volatile Set<ResourceKey<Biome>> scope = Set.of();
    private static final Map<ResourceKey<Biome>, Holder<Biome>> FLAT_BIOMES = new HashMap<>();

    private FlatRuntime() {
    }

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Flatland.MOD_ID, path));
    }

    public static void applyConfig() {
        flatHeight = FlatlandConfig.FLAT_HEIGHT.get();
        strength = FlatlandConfig.FLAT_STRENGTH.get() / 100.0F;
        variantMode = FlatlandConfig.VARIANT.get().name();

        Set<ResourceKey<Biome>> configured = new HashSet<>();
        for (String entry : FlatlandConfig.REPLACE_SCOPE.get()) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id == null) {
                LOGGER.warn("Ignoring invalid biome id in replace_scope: {}", entry);
                continue;
            }
            configured.add(ResourceKey.create(Registries.BIOME, id));
        }
        scope = Collections.unmodifiableSet(configured);
        active = strength > 0.0F;
        loaded = true;
    }

    public static void load(MinecraftServer server) {
        applyConfig();

        FLAT_BIOMES.clear();
        var registry = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (ResourceKey<Biome> flatKey : List.of(FLAT_PLAINS, FLAT_SNOWY, FLAT_SANDY)) {
            registry.getHolder(flatKey).ifPresentOrElse(
                    holder -> FLAT_BIOMES.put(flatKey, holder),
                    () -> LOGGER.warn("Flatland biome is missing from the datapack: {}", flatKey.location()));
        }

        if (FLAT_BIOMES.isEmpty()) {
            active = false;
        }
        LOGGER.info("Flatland active={} height={} strength={} scope={}", active, flatHeight, strength, scope.size());
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static boolean isActive() {
        return loaded && active;
    }

    public static float bandWeight(int blockX, int blockZ) {
        return FlatBand.weight(blockX, blockZ);
    }

    public static int flatHeight() {
        return flatHeight;
    }

    public static float strength() {
        return strength;
    }

    public static boolean spawnPrefer() {
        return FlatlandConfig.SPAWN_PREFER.get();
    }

    public static boolean isOverworldLike(NoiseGeneratorSettings settings) {
        return settings.seaLevel() == 63 && settings.defaultBlock().is(Blocks.STONE);
    }

    public static void seed(long seed) {
        FlatBand.seed(seed);
    }

    public static Holder<Biome> remap(Holder<Biome> source, int blockX, int blockZ) {
        if (!loaded || !active) {
            return null;
        }
        Optional<ResourceKey<Biome>> key = source.unwrapKey();
        if (key.isEmpty() || !scope.contains(key.get())) {
            return null;
        }
        if (FlatBand.weight(blockX, blockZ) < 0.5F) {
            return null;
        }
        return FLAT_BIOMES.get(pickVariant(source.value()));
    }

    public static boolean isFlatBiome(Holder<Biome> holder) {
        Optional<ResourceKey<Biome>> key = holder.unwrapKey();
        return key.isPresent() && FLAT_BIOMES.containsKey(key.get());
    }

    public static Stream<Holder<Biome>> possibleBiomes() {
        return FLAT_BIOMES.isEmpty() ? Stream.empty() : FLAT_BIOMES.values().stream();
    }

    private static ResourceKey<Biome> pickVariant(Biome biome) {
        switch (variantMode) {
            case "TEMPERATE":
                return FLAT_PLAINS;
            case "SNOWY":
                return FLAT_SNOWY;
            case "SANDY":
                return FLAT_SANDY;
            default:
                break;
        }
        float temperature = biome.getBaseTemperature();
        if (temperature <= 0.15F) {
            return FLAT_SNOWY;
        }
        if (temperature >= 1.0F) {
            return FLAT_SANDY;
        }
        return FLAT_PLAINS;
    }
}
