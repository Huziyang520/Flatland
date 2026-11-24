package com.huziyang.flatland.command;

import com.huziyang.flatland.world.FlatBand;
import com.huziyang.flatland.world.FlatRuntime;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

public final class FlatlandCommand {
    private FlatlandCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("flatland")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("probe")
                        .executes(ctx -> probe(ctx.getSource(), ctx.getSource().getPlayerOrException().blockPosition()))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> probe(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(String.format(
                "active=%s height=%d strength=%.2f",
                FlatRuntime.isLoaded() && FlatRuntime.strength() > 0.0F,
                FlatRuntime.flatHeight(),
                FlatRuntime.strength())), false);
        return 1;
    }

    private static int probe(CommandSourceStack source, BlockPos pos) {
        Level level = source.getLevel();
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        String biome = level.getBiome(pos).unwrapKey().map(key -> key.identifier().toString()).orElse("?");
        source.sendSuccess(() -> Component.literal(String.format(
                "x=%d z=%d w=%.3f top=%d biome=%s",
                pos.getX(), pos.getZ(), FlatBand.weight(pos.getX(), pos.getZ()), top, biome)), false);
        return 1;
    }
}
