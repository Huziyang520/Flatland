package com.huziyang.flatland.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.placement.CaveSurface;

public final class FlatSurfaceRules {
    private static volatile SurfaceRules.RuleSource original;
    private static volatile SurfaceRules.RuleSource wrapped;

    private FlatSurfaceRules() {
    }

    public static synchronized SurfaceRules.RuleSource wrap(SurfaceRules.RuleSource source) {
        if (source == original) {
            return wrapped;
        }
        SurfaceRules.RuleSource sandy = SurfaceRules.ifTrue(
                SurfaceRules.isBiome(FlatRuntime.FLAT_SANDY),
                SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(0, false, 0, CaveSurface.FLOOR),
                        SurfaceRules.state(Blocks.SAND.defaultBlockState())));
        // Appended last so the custom surface wins over the vanilla default for the same column.
        SurfaceRules.RuleSource result = SurfaceRules.sequence(source,
                SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), sandy));
        original = source;
        wrapped = result;
        return result;
    }
}
