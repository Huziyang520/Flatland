package com.huziyang.flatland.mixin;

import com.huziyang.flatland.world.FlatRuntime;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
    @Inject(method = "collectPossibleBiomes()Ljava/util/stream/Stream;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void flatland$possibleBiomes(CallbackInfoReturnable<Stream<Holder<Biome>>> cir) {
        Stream<Holder<Biome>> extra = FlatRuntime.possibleBiomes();
        if (extra == null) {
            return;
        }
        cir.setReturnValue(Stream.concat(cir.getReturnValue(), extra).distinct());
    }
}
