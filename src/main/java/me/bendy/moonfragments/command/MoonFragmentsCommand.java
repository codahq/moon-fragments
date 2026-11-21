package me.bendy.moonfragments.command;

import me.bendy.moonfragments.entity.FallingMoonFragment;
import me.bendy.moonfragments.spawn.MoonFragmentSpawner;
import me.bendy.moonfragments.spawn.MoonShowers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
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
 * {@code /moonfragments call <x> <z>}: a fragment falls exactly on that column, ignoring the rules.<br>
 * {@code /moonfragments shower [count] [seconds]}: a shower centred on you (see {@link #shower(CommandSourceStack, int, int)}).
 * Works at any time of day. Needs permission level 2 (gamemaster).
 */
public final class MoonFragmentsCommand {
    private static final SimpleCommandExceptionType NO_SPOT = new SimpleCommandExceptionType(
            Component.translatable("commands.moonfragments.call.no_spot"));
    private static final SimpleCommandExceptionType NO_PLAYERS = new SimpleCommandExceptionType(
            Component.translatable("commands.moonfragments.shower.no_players"));
    private static final Dynamic3CommandExceptionType TOO_FAST = new Dynamic3CommandExceptionType(
            (count, seconds, limit) -> Component.translatable("commands.moonfragments.shower.too_fast", count, seconds, limit));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("moonfragments")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("call")
                        .executes(ctx -> callNearPlayer(ctx.getSource()))
                        .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                                .executes(ctx -> callAt(ctx.getSource(), ColumnPosArgument.getColumnPos(ctx, "pos")))))
                .then(Commands.literal("shower")
                        .executes(ctx -> shower(ctx.getSource()))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, MoonShowers.MAX_FRAGMENTS))
                                .executes(ctx -> {
                                    int count = IntegerArgumentType.getInteger(ctx, "count");
                                    // As long as that many fragments take today on average, capped at the max duration.
                                    int ticks = (int) Math.min((long) count * MoonShowers.AVERAGE_GAP_TICKS,
                                            MoonShowers.MAX_DURATION_SECONDS * 20L);
                                    return shower(ctx.getSource(), count, ticks);
                                })
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MoonShowers.MAX_DURATION_SECONDS))
                                        .executes(ctx -> shower(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"),
                                                IntegerArgumentType.getInteger(ctx, "seconds") * 20))))));
    }

    /** {@code /moonfragments shower}: a natural shower (configured size, 1–4 s apart), centred on the caller. */
    private static int shower(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ServerPlayer center = MoonShowers.start(level, source.getPlayer(), level.getGameTime(),
                MoonShowers.randomCount(level.random), 0);
        if (center == null) {
            throw NO_PLAYERS.create();
        }
        source.sendSuccess(() -> Component.translatable("commands.moonfragments.shower.success", center.getDisplayName()), true);
        return 1;
    }

    /**
     * {@code /moonfragments shower <count> [seconds]}: {@code count} fragments spread evenly over {@code ticks}.
     * With only a count, the duration is what that many fragments take on average today (count × 2.5 s), capped at
     * {@value MoonShowers#MAX_DURATION_SECONDS} s. The rate is capped at {@value MoonShowers#MAX_FRAGMENTS_PER_SECOND}
     * per second.
     */
    private static int shower(CommandSourceStack source, int count, int ticks) throws CommandSyntaxException {
        float seconds = ticks / 20.0F;
        if (count > seconds * MoonShowers.MAX_FRAGMENTS_PER_SECOND) {
            throw TOO_FAST.create(count, formatSeconds(seconds), MoonShowers.MAX_FRAGMENTS_PER_SECOND);
        }
        ServerLevel level = source.getLevel();
        ServerPlayer center = MoonShowers.start(level, source.getPlayer(), level.getGameTime(), count, ticks);
        if (center == null) {
            throw NO_PLAYERS.create();
        }
        source.sendSuccess(() -> Component.translatable("commands.moonfragments.shower.success_sized",
                count, formatSeconds(seconds), center.getDisplayName()), true);
        return 1;
    }

    private static String formatSeconds(float seconds) {
        return seconds == Math.floor(seconds) ? Integer.toString((int) seconds) : Float.toString(seconds);
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
