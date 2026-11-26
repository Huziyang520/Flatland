package com.huziyang.flatland.mixin;

import com.huziyang.flatland.world.FlatMaterialRule;
import com.huziyang.flatland.world.FlatTerrain;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    private static final String BUILD_SURFACE =
            "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Ljava/util/Set;Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;)V";

    private BiomeSource flatland$biomeSource() {
        return ((ChunkGenerator) (Object) this).getBiomeSource();
    }

    @Inject(method = BUILD_SURFACE, at = @At("TAIL"), remap = false)
    private void flatland$finishSurface(ChunkAccess chunk, NoiseChunk noiseChunk, RandomState state,
            BiomeManager biomeManager, Set<Holder<Biome>> biomes, MaterialRule rule, CallbackInfo ci) {
        FlatTerrain.shave(chunk, flatland$biomeSource(), state);
    }

    @ModifyVariable(method = BUILD_SURFACE, at = @At("HEAD"), argsOnly = true, index = 6, remap = false)
    private MaterialRule flatland$materialRule(MaterialRule rule) {
        return rule instanceof FlatMaterialRule ? rule : new FlatMaterialRule(rule);
    }

    @Inject(method = "getBaseHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void flatland$baseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level,
            RandomState state, CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValue();
        int adjusted = FlatTerrain.adjustHeight(flatland$biomeSource(), state, x, z, vanilla);
        if (adjusted != vanilla) {
            cir.setReturnValue(adjusted);
        }
    }

    @Inject(method = "getBaseColumn(IILnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Lnet/minecraft/world/level/NoiseColumn;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void flatland$baseColumn(int x, int z, LevelHeightAccessor level, RandomState state,
            CallbackInfoReturnable<NoiseColumn> cir) {
        NoiseColumn adjusted = FlatTerrain.adjustColumn(flatland$biomeSource(), state, x, z, cir.getReturnValue(), level);
        if (adjusted != cir.getReturnValue()) {
            cir.setReturnValue(adjusted);
        }
    }
}
