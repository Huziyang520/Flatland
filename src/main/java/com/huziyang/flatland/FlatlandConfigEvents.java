package com.huziyang.flatland;

import com.huziyang.flatland.config.FlatlandConfig;
import com.huziyang.flatland.world.FlatRuntime;
import net.neoforged.fml.event.config.ModConfigEvent;

public final class FlatlandConfigEvents {
    private FlatlandConfigEvents() {
    }

    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == FlatlandConfig.SPEC) {
            FlatRuntime.applyConfig();
        }
    }

    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == FlatlandConfig.SPEC) {
            FlatRuntime.applyConfig();
        }
    }
}
