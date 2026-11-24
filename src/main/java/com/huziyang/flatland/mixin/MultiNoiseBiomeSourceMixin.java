package com.huziyang.flatland.mixin;

import com.huziyang.flatland.world.FlatRuntime;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
            at = @At("RETURN"), cancellable = true)
    private void flatland$remap(int x, int y, int z, Climate.Sampler sampler,
            CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        // getNoiseBiome receives quart coordinates; the band field is defined in block coordinates.
        Holder<Biome> replacement = FlatRuntime.remap(original, QuartPos.toBlock(x), QuartPos.toBlock(z));
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }

    @Inject(method = "collectPossibleBiomes()Ljava/util/stream/Stream;", at = @At("RETURN"), cancellable = true)
    private void flatland$possibleBiomes(CallbackInfoReturnable<Stream<Holder<Biome>>> cir) {
        Stream<Holder<Biome>> extra = FlatRuntime.possibleBiomes();
        if (extra == null) {
            return;
        }
        cir.setReturnValue(Stream.concat(cir.getReturnValue(), extra).distinct());
    }
}
