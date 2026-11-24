package com.huziyang.flatland;

import com.huziyang.flatland.config.FlatlandConfig;
import com.huziyang.flatland.world.FlatRuntime;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Flatland.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FlatlandConfigEvents {
    private FlatlandConfigEvents() {
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == FlatlandConfig.SPEC) {
            FlatRuntime.applyConfig();
        }
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == FlatlandConfig.SPEC) {
            FlatRuntime.applyConfig();
        }
    }
}
