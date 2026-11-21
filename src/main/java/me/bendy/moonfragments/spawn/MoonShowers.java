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

    private static final class Shower {
        final UUID center;
        int remaining;
        long nextTick;

        Shower(UUID center, int remaining, long nextTick) {
            this.center = center;
            this.remaining = remaining;
            this.nextTick = nextTick;
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
                    start(level, null, now + delay);
                }
            }
        }

        Iterator<Shower> it = state.showers.iterator();
        while (it.hasNext()) {
            Shower shower = it.next();
            if (now < shower.nextTick) {
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
            MoonFragmentSpawner.spawnNear(level, center.blockPosition(), level.random);
            shower.remaining--;
            shower.nextTick = now + MIN_GAP_TICKS + level.random.nextInt(MAX_GAP_TICKS - MIN_GAP_TICKS + 1);
            if (shower.remaining <= 0) {
                it.remove();
            }
        }
    }

    /**
     * Schedules a shower.
     * @param center the player to centre it on, or null to pick by crowd size
     * @return the player it's centred on, or null if there are no eligible players
     */
    public static @Nullable ServerPlayer start(ServerLevel level, @Nullable ServerPlayer center, long startTick) {
        if (center == null) {
            center = pickCenter(level, level.random);
            if (center == null) {
                return null;
            }
        }
        int min = Config.SHOWER_MIN_FRAGMENTS.getAsInt();
        int max = Math.max(min, Config.SHOWER_MAX_FRAGMENTS.getAsInt());
        int count = min + level.random.nextInt(max - min + 1);
        level.getData(ModAttachments.SHOWER_STATE).showers.add(new Shower(center.getUUID(), count, startTick));
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
