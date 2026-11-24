package com.huziyang.flatland.neoforge;

import com.huziyang.flatland.Flatland;
import com.huziyang.flatland.FlatlandServerHooks;
import com.huziyang.flatland.config.FlatlandSettings;
import com.huziyang.flatland.world.FlatRuntime;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@Mod(Flatland.MOD_ID)
public final class FlatlandNeoForge {
    public FlatlandNeoForge(IEventBus modBus, ModContainer container, Dist dist) {
        container.registerConfig(ModConfig.Type.COMMON, FlatlandNeoConfig.SPEC, "flatland-common.toml");
        modBus.addListener(FlatlandNeoConfig::onLoad);
        modBus.addListener(FlatlandNeoConfig::onReload);
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) ->
                FlatlandServerHooks.serverStarting(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                FlatlandServerHooks.serverStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                FlatlandServerHooks.registerCommands(event.getDispatcher()));
        if (dist.isClient()) {
            FlatlandNeoClient.register(container);
        }
    }
}
