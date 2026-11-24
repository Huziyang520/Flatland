package com.huziyang.flatland;

import com.huziyang.flatland.command.FlatlandCommand;
import com.huziyang.flatland.world.FlatRuntime;
import com.huziyang.flatland.world.FlatSpawn;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public final class FlatlandServerHooks {
    private FlatlandServerHooks() {
    }

    public static void serverStarting(MinecraftServer server) {
        FlatRuntime.load(server);
    }

    public static void serverStarted(MinecraftServer server) {
        FlatSpawn.applyOnFirstLoad(server);
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        FlatlandCommand.register(dispatcher);
    }
}
