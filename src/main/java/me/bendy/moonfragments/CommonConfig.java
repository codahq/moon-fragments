package me.bendy.moonfragments;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common (per-instance) config: config/moonfragments-common.toml.
 *
 * <p>These options shape enchantment tags, which are built when datapacks load. That happens before the
 * per-world server config is loaded, so they live here instead. On a server, the server's copy decides,
 * because clients receive the tags from the server. Changes apply after {@code /reload} or a restart.
 */
public final class CommonConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Where the Shrinksh and Growge enchantments can show up. Set a spot to false to keep them out of it.",
                "Applies to both enchantments. Changes take effect after /reload or a restart.",
                "On a server, the server's copy of this file decides.").push("sizeEnchantments");
    }

    public static final ModConfigSpec.BooleanValue SIZE_IN_ENCHANTING_TABLE = BUILDER
            .comment("Offered by the enchanting table. Off by default; opt in by setting true.")
            .define("enchantingTable", false);

    public static final ModConfigSpec.BooleanValue SIZE_ON_RANDOM_LOOT = BUILDER
            .comment("On enchanted books and gear in chest loot, fishing and other randomly enchanted loot.")
            .define("randomLoot", true);

    public static final ModConfigSpec.BooleanValue SIZE_TRADEABLE = BUILDER
            .comment("On enchanted books sold by librarian villagers.")
            .define("librarianBooks", true);

    public static final ModConfigSpec.BooleanValue SIZE_ON_TRADED_EQUIPMENT = BUILDER
            .comment("On enchanted armor sold by villagers (armorers). Off by default; opt in by setting true.")
            .define("villagerEquipment", false);

    public static final ModConfigSpec.BooleanValue SIZE_ON_MOB_SPAWN_EQUIPMENT = BUILDER
            .comment("On enchanted armor that zombies, skeletons and other mobs spawn wearing.")
            .define("mobSpawnEquipment", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** A vanilla enchantment tag that Shrinksh and Growge join through {@code #minecraft:non_treasure}, and the option gating it. */
    public record SizeSpot(TagKey<Enchantment> tag, BooleanSupplier enabled) {
    }

    public static final List<SizeSpot> SIZE_SPOTS = List.of(
            new SizeSpot(EnchantmentTags.IN_ENCHANTING_TABLE, SIZE_IN_ENCHANTING_TABLE::getAsBoolean),
            new SizeSpot(EnchantmentTags.ON_RANDOM_LOOT, SIZE_ON_RANDOM_LOOT::getAsBoolean),
            new SizeSpot(EnchantmentTags.TRADEABLE, SIZE_TRADEABLE::getAsBoolean),
            new SizeSpot(EnchantmentTags.ON_TRADED_EQUIPMENT, SIZE_ON_TRADED_EQUIPMENT::getAsBoolean),
            new SizeSpot(EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT, SIZE_ON_MOB_SPAWN_EQUIPMENT::getAsBoolean));

    private CommonConfig() {
    }
}
