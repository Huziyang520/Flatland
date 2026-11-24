package com.huziyang.flatland;

import com.huziyang.flatland.command.FlatlandCommand;
import com.huziyang.flatland.world.FlatRuntime;
import com.huziyang.flatland.world.FlatSpawn;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class FlatlandEvents {
    private FlatlandEvents() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        FlatRuntime.load(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        FlatSpawn.applyOnFirstLoad(event.getServer());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FlatlandCommand.register(event.getDispatcher());
    }
}
