package com.codahq.moonfragments.util;

import net.minecraft.world.level.Level;

/** Day/night by world clock (not sky brightness, so rain and thunder don't count as night). */
public final class MoonTime {
    public static final long DAY_LENGTH = 24000L;
    /** Roughly when hostile mobs start spawning. */
    public static final long NIGHT_START = 13000L;
    /** Roughly sunrise. */
    public static final long NIGHT_END = 23000L;
    public static final long NIGHT_LENGTH = NIGHT_END - NIGHT_START;

    /** True at night. Dimensions with fixed time (Nether, End) have no sunrise, so they always count as night. */
    public static boolean isNight(Level level) {
        if (level.dimensionType().hasFixedTime()) {
            return true;
        }
        long time = Math.floorMod(level.getDayTime(), DAY_LENGTH);
        return time >= NIGHT_START && time < NIGHT_END;
    }

    private MoonTime() {
    }
}
