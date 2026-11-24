package com.huziyang.flatland.fabric;

import com.huziyang.flatland.Flatland;
import fuzs.forgeconfigapiport.fabric.api.v5.client.ConfigScreenFactoryRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

public final class FlatlandFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ConfigScreenFactoryRegistry.INSTANCE.register(Flatland.MOD_ID,
                (modId, parent) -> new ConfigurationScreen(modId, parent));
    }
}
