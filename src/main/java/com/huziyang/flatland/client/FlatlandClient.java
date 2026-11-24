package com.huziyang.flatland.client;

import com.huziyang.flatland.Flatland;
import com.huziyang.neoconfigurationscreenport.forge.ConfigScreenRegistration;

public final class FlatlandClient {
    private FlatlandClient() {
    }

    public static void register() {
        ConfigScreenRegistration.register(Flatland.MOD_ID);
    }
}
