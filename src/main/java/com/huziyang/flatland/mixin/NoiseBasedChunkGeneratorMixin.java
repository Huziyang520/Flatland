package com.huziyang.flatland.mixin;

import com.huziyang.flatland.world.FlatSurfaceRules;
import com.huziyang.flatland.world.FlatTerrain;
import net.minecraft.core.Registry;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    private BiomeSource flatland$biomeSource() {
        return ((ChunkGenerator) (Object) this).getBiomeSource();
    }

    @Inject(method = "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/blending/Blender;)V",
            at = @At("TAIL"))
    private void flatland$shave(ChunkAccess chunk, WorldGenerationContext context, RandomState state,
            StructureManager structures, BiomeManager biomes, Registry<Biome> registry, Blender blender,
            CallbackInfo ci) {
        FlatTerrain.shave(chunk, flatland$biomeSource(), state);
    }

    @Inject(method = "getBaseHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I",
            at = @At("RETURN"), cancellable = true)
    private void flatland$baseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level,
            RandomState state, CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValue();
        int adjusted = FlatTerrain.adjustHeight(flatland$biomeSource(), state, x, z, vanilla);
        if (adjusted != vanilla) {
            cir.setReturnValue(adjusted);
        }
    }

    @Inject(method = "getBaseColumn(IILnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Lnet/minecraft/world/level/NoiseColumn;",
            at = @At("RETURN"), cancellable = true)
    private void flatland$baseColumn(int x, int z, LevelHeightAccessor level, RandomState state,
            CallbackInfoReturnable<NoiseColumn> cir) {
        NoiseColumn adjusted = FlatTerrain.adjustColumn(flatland$biomeSource(), state, x, z, cir.getReturnValue(), level);
        if (adjusted != cir.getReturnValue()) {
            cir.setReturnValue(adjusted);
        }
    }

    @Redirect(method = "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/blending/Blender;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;surfaceRule()Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;"))
    private SurfaceRules.RuleSource flatland$surfaceRule(NoiseGeneratorSettings settings) {
        return FlatSurfaceRules.wrap(settings.surfaceRule());
    }
}
