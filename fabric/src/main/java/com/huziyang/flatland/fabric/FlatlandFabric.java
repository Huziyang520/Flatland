package com.huziyang.flatland.fabric;

import com.huziyang.flatland.Flatland;
import com.huziyang.flatland.FlatlandServerHooks;
import com.huziyang.flatland.config.FlatlandSettings;
import com.huziyang.flatland.world.FlatRuntime;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.neoforged.fml.config.ModConfig;

public final class FlatlandFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ConfigRegistry.INSTANCE.register(Flatland.MOD_ID, ModConfig.Type.COMMON,
                FlatlandFabricConfig.SPEC, "flatland-common.toml");

        ModConfigEvents.loading(Flatland.MOD_ID).register(config ->
                FlatRuntime.applyConfig(FlatlandFabricConfig.read()));
        ModConfigEvents.reloading(Flatland.MOD_ID).register(config ->
                FlatRuntime.applyConfig(FlatlandFabricConfig.read()));

        ServerLifecycleEvents.SERVER_STARTING.register(FlatlandServerHooks::serverStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(FlatlandServerHooks::serverStarted);
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                FlatlandServerHooks.registerCommands(dispatcher));
    }
}
