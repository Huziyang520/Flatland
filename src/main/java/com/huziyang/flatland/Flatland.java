package com.huziyang.flatland;

import com.huziyang.flatland.client.FlatlandClient;
import com.huziyang.flatland.config.FlatlandConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Flatland.MOD_ID)
public final class Flatland {
    public static final String MOD_ID = "flatland";

    public Flatland(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, FlatlandConfig.SPEC, "flatland-common.toml");
        modBus.addListener(FlatlandConfigEvents::onConfigLoad);
        modBus.addListener(FlatlandConfigEvents::onConfigReload);
        NeoForge.EVENT_BUS.register(FlatlandEvents.class);
        if (FMLLoader.getDist().isClient()) {
            FlatlandClient.register(container);
        }
    }
}
