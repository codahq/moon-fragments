package me.bendy.moonfragments.spawn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import me.bendy.moonfragments.Config;
import me.bendy.moonfragments.registry.ModAttachments;
import me.bendy.moonfragments.util.MoonTime;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Moon fragment showers (issue #4): about once every 5 nights, 15–20 fragments fall over a minute or so.
 * One shower per world, not per player, in addition to the normal nightly falls.
 * <p>
 * The centre is picked by weighted random: each player's weight is the square of the number of players near them, so
 * crowds are strongly favoured but a lone player can still be chosen.
 * <p>
 * Nothing is saved to disk. The once-per-night roll and any shower in progress live in a non-serialized level
 * attachment ({@link State}), so a server restart can re-roll the current night or cut a shower short.
 */
public final class MoonShowers {
    /** Players within this many blocks of each other count as one crowd. */
    private static final double CROWD_RADIUS = 128.0;
    private static final int MIN_GAP_TICKS = 20;
    private static final int MAX_GAP_TICKS = 80;
    /**
     * Average gap of the natural pacing (1–4 s, so 2.5 s). {@code /moonfragments shower <count>} takes as long as that
     * many fragments take on average: {@code count} × this, capped at {@link #MAX_DURATION_SECONDS}.
     */
    public static final int AVERAGE_GAP_TICKS = (MIN_GAP_TICKS + MAX_GAP_TICKS) / 2;
    /** Command caps: a shower lasts at most this long… */
    public static final int MAX_DURATION_SECONDS = 10 * 60;
    /** …and drops at most this many fragments per second. */
    public static final int MAX_FRAGMENTS_PER_SECOND = 1000;
    public static final int MAX_FRAGMENTS = MAX_DURATION_SECONDS * MAX_FRAGMENTS_PER_SECOND;

    private static final class Shower {
        final UUID center;
        final int total;
        final long startTick;
        /** 0 = natural pacing (random 1–4 s gaps); otherwise spread {@link #total} evenly over this many ticks. */
        final int durationTicks;
        int spawned;
        long nextTick;

        Shower(UUID center, int total, long startTick, int durationTicks) {
            this.center = center;
            this.total = total;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
            this.nextTick = startTick;
        }

        /** How many fragments should have fallen by {@code now} (always at least 1 per due step). */
        int due(long now) {
            if (durationTicks <= 0) {
                return now >= nextTick ? spawned + 1 : spawned;
            }
            long elapsed = now - startTick + 1;
            return (int) Math.min(total, Math.ceil((double) total * elapsed / durationTicks));
        }
    }

    /** Per-level shower bookkeeping, held in the {@link ModAttachments#SHOWER_STATE} level attachment (not saved). */
    public static final class State {
        long lastRolledNight = Long.MIN_VALUE;
        final List<Shower> showers = new ArrayList<>();
    }

    /** Called every server level tick. */
    public static void tick(ServerLevel level) {
        State state = level.getData(ModAttachments.SHOWER_STATE);
        long now = level.getGameTime();

        if (MoonFragmentSpawner.canFallIn(level) && MoonTime.isNight(level)) {
            long night = Math.floorDiv(level.getDayTime(), MoonTime.DAY_LENGTH);
            if (night != state.lastRolledNight) {
                state.lastRolledNight = night;
                if (level.random.nextDouble() < Config.SHOWER_CHANCE_PER_NIGHT.get()) {
                    // Start at a random point in the first half of the night so it isn't always at dusk.
                    long delay = level.random.nextInt((int) (MoonTime.NIGHT_LENGTH / 2));
                    start(level, null, now + delay, randomCount(level.random), 0);
                }
            }
        }

        Iterator<Shower> it = state.showers.iterator();
        while (it.hasNext()) {
            Shower shower = it.next();
            if (now < shower.startTick) {
                continue;
            }
            int due = shower.due(now);
            if (due <= shower.spawned) {
                continue;
            }
            ServerPlayer center = level.getServer().getPlayerList().getPlayer(shower.center);
            if (center == null || center.level() != level) {
                // Centre player left: hand the shower to another crowd, or end it.
                ServerPlayer replacement = pickCenter(level, level.random);
                if (replacement == null) {
                    it.remove();
                    continue;
                }
                center = replacement;
            }
            // At high rates several fall in one tick. A spot with no valid landing still counts, so the shower ends on time.
            while (shower.spawned < due) {
                MoonFragmentSpawner.spawnNear(level, center.blockPosition(), level.random);
                shower.spawned++;
            }
            shower.nextTick = now + MIN_GAP_TICKS + level.random.nextInt(MAX_GAP_TICKS - MIN_GAP_TICKS + 1);
            if (shower.spawned >= shower.total) {
                it.remove();
            }
        }
    }

    /** A natural shower's size: between the configured min and max fragments. */
    public static int randomCount(RandomSource random) {
        int min = Config.SHOWER_MIN_FRAGMENTS.getAsInt();
        int max = Math.max(min, Config.SHOWER_MAX_FRAGMENTS.getAsInt());
        return min + random.nextInt(max - min + 1);
    }

    /**
     * Schedules a shower.
     * @param center the player to centre it on, or null to pick by crowd size
     * @param count how many fragments fall
     * @param durationTicks 0 for natural pacing (random 1–4 s gaps), otherwise spread evenly over this many ticks
     * @return the player it's centred on, or null if there are no eligible players
     */
    public static @Nullable ServerPlayer start(ServerLevel level, @Nullable ServerPlayer center, long startTick, int count,
            int durationTicks) {
        if (center == null) {
            center = pickCenter(level, level.random);
            if (center == null) {
                return null;
            }
        }
        level.getData(ModAttachments.SHOWER_STATE).showers.add(new Shower(center.getUUID(), count, startTick, durationTicks));
        if (Config.SHOWER_ANNOUNCE.getAsBoolean()) {
            Component message = Component.translatable("message.moonfragments.shower").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC);
            for (ServerPlayer player : level.players()) {
                player.sendSystemMessage(message);
            }
        }
        return center;
    }

    /** Weighted random pick: weight = (players within {@value #CROWD_RADIUS} blocks, including self)². */
    private static @Nullable ServerPlayer pickCenter(ServerLevel level, RandomSource random) {
        List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
        if (players.isEmpty()) {
            return null;
        }
        double radiusSqr = CROWD_RADIUS * CROWD_RADIUS;
        long[] weights = new long[players.size()];
        long total = 0;
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            long crowd = players.stream().filter(o -> o.distanceToSqr(p) <= radiusSqr).count();
            weights[i] = crowd * crowd;
            total += weights[i];
        }
        long roll = (long) (random.nextDouble() * total);
        for (int i = 0; i < players.size(); i++) {
            roll -= weights[i];
            if (roll < 0) {
                return players.get(i);
            }
        }
        return players.getLast();
    }

    private MoonShowers() {
    }
}
