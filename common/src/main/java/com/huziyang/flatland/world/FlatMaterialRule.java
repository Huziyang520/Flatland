package com.huziyang.flatland.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;

/**
 * Decorates the vanilla material rule so the sandy flat variant gets a sand surface on the
 * topmost solid block. It mirrors the surface-rule wrapper used on branches that still have
 * SurfaceRules, and is a no-op for the other variants.
 */
public final class FlatMaterialRule implements MaterialRule {
    private final MaterialRule delegate;

    public FlatMaterialRule(MaterialRule delegate) {
        this.delegate = delegate;
    }

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        RuleEvaluator evaluator = this.delegate.compile(context);
        return (blockX, blockY, blockZ) -> {
            if (context.stoneDepthAbove() == 0
                    && context.stoneDepthBelow() == 0
                    && FlatRuntime.isSandyBiome(context.getBiome())) {
                return Blocks.SAND.defaultBlockState();
            }
            return evaluator.tryApply(blockX, blockY, blockZ);
        };
    }

    @Override
    public MapCodec<? extends MaterialRule> codec() {
        throw new UnsupportedOperationException("FlatMaterialRule is a runtime decorator");
    }
}
