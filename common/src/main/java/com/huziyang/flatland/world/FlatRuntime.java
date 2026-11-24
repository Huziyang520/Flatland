package com.huziyang.flatland.world;

import com.huziyang.flatland.Flatland;
import com.huziyang.flatland.config.FlatlandSettings;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
    private static volatile boolean spawnPrefer = true;
    private static volatile int configVersion = 1;
    private static volatile Set<ResourceKey<Biome>> scope = Set.of();
    private static volatile Map<ResourceKey<Biome>, Holder<Biome>> flatBiomes = Map.of();

    private FlatRuntime() {
    }

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Flatland.MOD_ID, path));
    }

    public static void applyConfig(FlatlandSettings settings) {
        flatHeight = Math.max(62, Math.min(70, settings.flatHeight()));
        strength = Math.max(0, Math.min(100, settings.flatStrength())) / 100.0F;
        variantMode = settings.variant().toUpperCase(Locale.ROOT);
        spawnPrefer = settings.spawnPrefer();
        configVersion = settings.configVersion();

        Set<ResourceKey<Biome>> configured = new HashSet<>();
        for (String entry : settings.replaceScope()) {
            Identifier id = Identifier.tryParse(entry);
            if (id == null) {
                LOGGER.warn("Ignoring invalid biome id in replace_scope: {}", entry);
                continue;
            }
            configured.add(ResourceKey.create(Registries.BIOME, id));
        }
        scope = Collections.unmodifiableSet(configured);
        active = strength > 0.0F && !configured.isEmpty() && !flatBiomes.isEmpty();
        loaded = true;
    }

    public static void load(MinecraftServer server) {
        if (!loaded) {
            applyConfig(FlatlandSettings.defaults());
        }

        Map<ResourceKey<Biome>, Holder<Biome>> resolved = new HashMap<>(3);
        var registry = server.registryAccess().lookupOrThrow(Registries.BIOME);
        for (ResourceKey<Biome> flatKey : List.of(FLAT_PLAINS, FLAT_SNOWY, FLAT_SANDY)) {
            registry.get(flatKey).ifPresentOrElse(
                    holder -> resolved.put(flatKey, holder),
                    () -> LOGGER.warn("Flatland biome is missing from the datapack: {}", flatKey.identifier()));
        }
        flatBiomes = Map.copyOf(resolved);
        // Recompute instead of only clearing: a second world in the same JVM reloads scope and
        // biomes independently, so a stale "true" from the first world would survive.
        active = strength > 0.0F && !scope.isEmpty() && !flatBiomes.isEmpty();
        LOGGER.info("Flatland active={} height={} strength={} scope={}", active, flatHeight, strength, scope.size());
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
        return spawnPrefer;
    }

    public static String variantMode() {
        return variantMode;
    }

    public static int scopeSize() {
        return scope.size();
    }

    public static int configVersion() {
        return configVersion;
    }

    public static boolean isOverworldLike(BlockState defaultBlock, int seaLevel) {
        return seaLevel > 0 && defaultBlock.is(Blocks.STONE);
    }

    public static void seed(long seed) {
        FlatBand.seed(seed);
    }

    public static Holder<Biome> remap(Holder<Biome> source, int blockX, int blockZ) {
        Map<ResourceKey<Biome>, Holder<Biome>> biomes = flatBiomes;
        if (!loaded || !active || biomes.isEmpty()) {
            return null;
        }
        Optional<ResourceKey<Biome>> key = source.unwrapKey();
        if (key.isEmpty() || !scope.contains(key.get())) {
            return null;
        }
        if (FlatBand.weight(blockX, blockZ) < 0.5F) {
            return null;
        }
        return biomes.get(pickVariant(source.value()));
    }

    public static boolean isFlatBiome(Holder<Biome> holder) {
        return holder.is(FLAT_PLAINS) || holder.is(FLAT_SNOWY) || holder.is(FLAT_SANDY);
    }

    public static boolean isSandyBiome(Holder<Biome> holder) {
        return holder.is(FLAT_SANDY);
    }

    public static Stream<Holder<Biome>> possibleBiomes() {
        Map<ResourceKey<Biome>, Holder<Biome>> biomes = flatBiomes;
        return biomes.isEmpty() ? Stream.empty() : biomes.values().stream();
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
