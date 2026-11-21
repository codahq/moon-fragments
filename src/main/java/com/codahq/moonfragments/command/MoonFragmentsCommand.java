package com.codahq.moonfragments.command;

import com.codahq.moonfragments.entity.FallingMoonFragment;
import com.codahq.moonfragments.spawn.MoonFragmentSpawner;
import com.codahq.moonfragments.spawn.MoonShowers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * {@code /moonfragments call}: a fragment falls near you, following the normal landing rules.<br>
 * {@code /moonfragments call <x> <z>}: a fragment falls exactly on that column, ignoring the rules.
 * Works at any time of day. Needs permission level 2 (gamemaster).
 */
public final class MoonFragmentsCommand {
    private static final SimpleCommandExceptionType NO_SPOT = new SimpleCommandExceptionType(
            Component.translatable("commands.moonfragments.call.no_spot"));
    private static final SimpleCommandExceptionType NO_PLAYERS = new SimpleCommandExceptionType(
            Component.translatable("commands.moonfragments.shower.no_players"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("moonfragments")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("call")
                        .executes(ctx -> callNearPlayer(ctx.getSource()))
                        .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                                .executes(ctx -> callAt(ctx.getSource(), ColumnPosArgument.getColumnPos(ctx, "pos")))))
                .then(Commands.literal("shower")
                        .executes(ctx -> shower(ctx.getSource()))));
    }

    /** Starts a shower now, centred on the calling player (or the biggest crowd if run from the console). */
    private static int shower(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ServerPlayer caller = source.getPlayer();
        ServerPlayer center = MoonShowers.start(level, caller, level.getGameTime());
        if (center == null) {
            throw NO_PLAYERS.create();
        }
        source.sendSuccess(() -> Component.translatable("commands.moonfragments.shower.success", center.getDisplayName()), true);
        return 1;
    }

    private static int callNearPlayer(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        FallingMoonFragment fragment = MoonFragmentSpawner.spawnNear(level, player.blockPosition(), level.random);
        if (fragment == null) {
            throw NO_SPOT.create();
        }
        report(source, fragment);
        return 1;
    }

    private static int callAt(CommandSourceStack source, ColumnPos column) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        BlockPos probe = new BlockPos(column.x(), level.getSeaLevel(), column.z());
        boolean ticking = level.isLoaded(probe) && level.isPositionEntityTicking(probe);
        if (!ticking) {
            // Force-load briefly: the fragment keeps the ticket alive until it has landed and settled.
            ChunkPos chunk = new ChunkPos(probe);
            FallingMoonFragment.placeChunkTicket(level, chunk);
            level.getChunk(chunk.x, chunk.z); // load/generate now so the heightmap is valid
        }
        BlockPos landing = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, probe).below();
        FallingMoonFragment fragment = MoonFragmentSpawner.spawnAbove(level, landing);
        fragment.setKeepLoaded(!ticking);
        report(source, fragment);
        return 1;
    }

    private static void report(CommandSourceStack source, FallingMoonFragment fragment) {
        BlockPos pos = fragment.blockPosition();
        source.sendSuccess(() -> Component.translatable("commands.moonfragments.call.success", pos.getX(), pos.getZ()), true);
    }

    private MoonFragmentsCommand() {
    }
}
