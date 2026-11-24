package com.huziyang.flatland;

import com.huziyang.flatland.client.FlatlandClient;
import com.huziyang.flatland.config.FlatlandConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(Flatland.MOD_ID)
public final class Flatland {
    public static final String MOD_ID = "flatland";

    public Flatland() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FlatlandConfig.SPEC, "flatland-common.toml");
        MinecraftForge.EVENT_BUS.register(FlatlandEvents.class);
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> FlatlandClient.register());
    }
}
