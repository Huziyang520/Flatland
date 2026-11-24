package com.huziyang.flatland.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class FlatlandConfig {
    public enum Variant {
        AUTO,
        TEMPERATE,
        SNOWY,
        SANDY
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REPLACE_SCOPE;
    public static final ModConfigSpec.IntValue FLAT_HEIGHT;
    public static final ModConfigSpec.IntValue FLAT_STRENGTH;
    public static final ModConfigSpec.BooleanValue SPAWN_PREFER;
    public static final ModConfigSpec.EnumValue<Variant> VARIANT;
    public static final ModConfigSpec.IntValue CONFIG_VERSION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("World generation. Changes only affect chunks that have not been generated yet.",
                "COMMON config: editable from the main-menu mod list; a dedicated server reads its own copy.").push("worldgen");

        REPLACE_SCOPE = builder
                .comment("Biome ids converted into flatland. Any biome id is accepted; unknown ids are ignored.",
                        "Defaults to the plains family plus desert, so all three variants can appear.")
                .defineListAllowEmpty("replace_scope",
                        List.of("minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:desert"),
                        o -> o instanceof String s && s.indexOf(':') > 0);

        FLAT_HEIGHT = builder
                .comment("Y of the top solid block. 62 keeps the ground flush with the sea surface and leaves the block above as air.")
                .defineInRange("flat_height", 62, 55, 70);

        FLAT_STRENGTH = builder
                .comment("0 keeps vanilla terrain, 100 makes the converted area perfectly flat.",
                        "Values in between produce a gentler version of the same shape.")
                .defineInRange("flat_strength", 100, 0, 100);

        VARIANT = builder
                .comment("AUTO picks by the replaced biome's temperature: cold -> snowy, hot -> sandy, otherwise temperate.")
                .defineEnum("variant", Variant.AUTO);

        SPAWN_PREFER = builder
                .comment("Move the world spawn into flatland once, on the first load of a world. Never touches existing worlds twice.")
                .define("spawn_prefer_flatland", true);

        CONFIG_VERSION = builder
                .comment("Internal. Bumped when a one-time migration is needed.")
                .defineInRange("config_version", 1, 0, Integer.MAX_VALUE);

        builder.pop();
        SPEC = builder.build();
    }

    private FlatlandConfig() {
    }
}
