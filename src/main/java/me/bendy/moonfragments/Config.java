package me.bendy.moonfragments;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server (per-world) config: serverconfig/moonfragments-server.toml. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Impact explosion when a falling moon fragment hits the ground.").push("impact");
    }

    public static final ModConfigSpec.DoubleValue IMPACT_EXPLOSION_POWER = BUILDER
            .comment("Explosion power. TNT is 4.0. 0 disables the explosion entirely (effects only).")
            .defineInRange("explosionPower", 4.0, 0.0, 10.0);

    public static final ModConfigSpec.BooleanValue IMPACT_BREAKS_BLOCKS = BUILDER
            .comment("Whether the impact breaks blocks. Off by default; opt in by setting true.",
                    "Even when true, blocks only break if the mobGriefing gamerule is on.")
            .define("breakBlocks", false);

    public static final ModConfigSpec.BooleanValue IMPACT_DAMAGES_ENTITIES = BUILDER
            .comment("Whether the impact damages and knocks back players, mobs and other entities. Off by default; opt in by setting true.")
            .define("damageEntities", false);

    static {
        BUILDER.pop();
        BUILDER.comment("Nightly moon fragment falls near each player.").push("spawning");
    }

    public static final ModConfigSpec.DoubleValue FRAGMENTS_PER_NIGHT = BUILDER
            .comment("0.5 = about a 50% chance each night for 1 fragment to fall per player")
            .defineInRange("fragmentsPerNight", 0.5, 0.0, 100.0);

    public static final ModConfigSpec.IntValue MIN_SPAWN_DISTANCE = BUILDER
            .comment("Minimum horizontal distance from the player. Keep it above 15 so the beacon shows before the fragment is revealed.")
            .defineInRange("minDistance", 24, 0, 256);

    public static final ModConfigSpec.IntValue MAX_SPAWN_DISTANCE = BUILDER
            .comment("Maximum horizontal distance from the player. The landing spot must be in a loaded, entity-ticking chunk.")
            .defineInRange("maxDistance", 64, 1, 256);

    static {
        BUILDER.pop();
        BUILDER.comment("Moon fragment showers: one per world, centred on the biggest crowd of players (usually).").push("showers");
    }

    public static final ModConfigSpec.DoubleValue SHOWER_CHANCE_PER_NIGHT = BUILDER
            .comment("0.05 = about a 5% chance of a shower each night")
            .defineInRange("chancePerNight", 0.05, 0.0, 1.0);

    public static final ModConfigSpec.IntValue SHOWER_MIN_FRAGMENTS = BUILDER
            .comment("Fewest fragments in a shower.")
            .defineInRange("minFragments", 15, 1, 200);

    public static final ModConfigSpec.IntValue SHOWER_MAX_FRAGMENTS = BUILDER
            .comment("Most fragments in a shower.")
            .defineInRange("maxFragments", 20, 1, 200);

    public static final ModConfigSpec.BooleanValue SHOWER_ANNOUNCE = BUILDER
            .comment("Show a chat message to players in the dimension when a shower begins.")
            .define("announce", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
