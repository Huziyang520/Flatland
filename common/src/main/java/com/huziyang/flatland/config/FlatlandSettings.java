package com.huziyang.flatland.config;

import java.util.List;

public record FlatlandSettings(
        List<String> replaceScope,
        int flatHeight,
        int flatStrength,
        String variant,
        boolean spawnPrefer) {
    public static FlatlandSettings defaults() {
        return new FlatlandSettings(
                List.of("minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:desert"),
                62,
                100,
                "AUTO",
                true);
    }
}
