package me.bendy.moonfragments.registry;

import java.util.EnumMap;
import java.util.Map;

import me.bendy.moonfragments.MoonFragments;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Moon fragment tool and armor materials (issue #2).
 * Durability matches netherite; damage, speed and toughness sit halfway between diamond and netherite;
 * enchantability matches gold (the highest in the game).
 */
public final class ModMaterials {
    /** Items that repair moon fragment gear (just the fragment). */
    public static final TagKey<Item> REPAIRS_MOON_FRAGMENT_GEAR =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MoonFragments.MODID, "repairs_moon_fragment_gear"));

    public static final ToolMaterial MOON_FRAGMENT_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL, // mines everything netherite can
            2031,  // durability: netherite
            8.5F,  // mining speed: diamond 8, netherite 9
            3.5F,  // attack damage bonus: diamond 3, netherite 4
            22,    // enchantability: gold tools
            REPAIRS_MOON_FRAGMENT_GEAR);

    public static final ArmorMaterial MOON_FRAGMENT_ARMOR = new ArmorMaterial(
            37,                       // durability multiplier: netherite
            defense(3, 6, 8, 3, 15),  // diamond and netherite share 3/6/8/3; body is halfway (11/19)
            25,                       // enchantability: gold armor
            SoundEvents.ARMOR_EQUIP_DIAMOND,
            2.5F,                     // toughness: diamond 2, netherite 3
            0.05F,                    // knockback resistance: diamond 0, netherite 0.1
            REPAIRS_MOON_FRAGMENT_GEAR,
            equipmentAsset("moon_fragment"));

    /** Same stats as {@link #MOON_FRAGMENT_ARMOR}; only the worn look differs (its own equipment asset and model). */
    public static final ArmorMaterial MOON_FRAGMENT_DRESS_ARMOR = withLook(MOON_FRAGMENT_ARMOR, "moon_fragment_dress");
    /** Same stats as {@link #MOON_FRAGMENT_ARMOR}; only the worn look differs (its own equipment asset and model). */
    public static final ArmorMaterial MOON_FRAGMENT_SKIRT_ARMOR = withLook(MOON_FRAGMENT_ARMOR, "moon_fragment_skirt");

    /** {@code moonfragments:<name>}, resolved to {@code assets/moonfragments/equipment/<name>.json}. */
    private static ResourceKey<EquipmentAsset> equipmentAsset(String name) {
        return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MoonFragments.MODID, name));
    }

    /** A copy of {@code base} that renders with a different equipment asset. */
    private static ArmorMaterial withLook(ArmorMaterial base, String assetName) {
        return new ArmorMaterial(base.durability(), base.defense(), base.enchantmentValue(), base.equipSound(),
                base.toughness(), base.knockbackResistance(), base.repairIngredient(), equipmentAsset(assetName));
    }

    private static Map<ArmorType, Integer> defense(int boots, int leggings, int chestplate, int helmet, int body) {
        Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
        map.put(ArmorType.BOOTS, boots);
        map.put(ArmorType.LEGGINGS, leggings);
        map.put(ArmorType.CHESTPLATE, chestplate);
        map.put(ArmorType.HELMET, helmet);
        map.put(ArmorType.BODY, body);
        return map;
    }

    private ModMaterials() {
    }
}
