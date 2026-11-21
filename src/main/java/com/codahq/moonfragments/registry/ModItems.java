package com.codahq.moonfragments.registry;

import static com.codahq.moonfragments.registry.ModMaterials.MOON_FRAGMENT_ARMOR;
import static com.codahq.moonfragments.registry.ModMaterials.MOON_FRAGMENT_TOOL;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.codahq.moonfragments.MoonFragments;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All moon fragment items. Every item is fire resistant like netherite (issue #2).
 * Base damage/speed arguments mirror the vanilla diamond/netherite items; the material supplies the bonus.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MoonFragments.MODID);

    public static final DeferredItem<Item> MOON_FRAGMENT = item("moon_fragment", p -> p);

    // Tools and weapons
    public static final DeferredItem<Item> MOON_FRAGMENT_SWORD = item("moon_fragment_sword",
            p -> p.sword(MOON_FRAGMENT_TOOL, 3.0F, -2.4F));
    public static final DeferredItem<ShovelItem> MOON_FRAGMENT_SHOVEL = toolItem("moon_fragment_shovel",
            p -> new ShovelItem(MOON_FRAGMENT_TOOL, 1.5F, -3.0F, p));
    public static final DeferredItem<Item> MOON_FRAGMENT_PICKAXE = item("moon_fragment_pickaxe",
            p -> p.pickaxe(MOON_FRAGMENT_TOOL, 1.0F, -2.8F));
    public static final DeferredItem<AxeItem> MOON_FRAGMENT_AXE = toolItem("moon_fragment_axe",
            p -> new AxeItem(MOON_FRAGMENT_TOOL, 5.0F, -3.0F, p));
    // Diamond hoe uses -3, netherite -4; both land on 0 total damage after the material bonus.
    public static final DeferredItem<HoeItem> MOON_FRAGMENT_HOE = toolItem("moon_fragment_hoe",
            p -> new HoeItem(MOON_FRAGMENT_TOOL, -3.5F, 0.0F, p));
    // Spear (new in 1.21.11): every parameter is the midpoint of the diamond and netherite spears.
    public static final DeferredItem<Item> MOON_FRAGMENT_SPEAR = item("moon_fragment_spear",
            p -> p.spear(MOON_FRAGMENT_TOOL, 1.10F, 1.1375F, 0.45F, 2.75F, 7.25F, 6.0F, 5.1F, 9.375F, 4.6F));

    // Armor
    public static final DeferredItem<Item> MOON_FRAGMENT_HELMET = armor("moon_fragment_helmet", MOON_FRAGMENT_ARMOR, ArmorType.HELMET);
    public static final DeferredItem<Item> MOON_FRAGMENT_CHESTPLATE = armor("moon_fragment_chestplate", MOON_FRAGMENT_ARMOR, ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> MOON_FRAGMENT_LEGGINGS = armor("moon_fragment_leggings", MOON_FRAGMENT_ARMOR, ArmorType.LEGGINGS);
    /** Same as the leggings in every way except the worn model: a dress. */
    public static final DeferredItem<Item> MOON_FRAGMENT_DRESS = armor("moon_fragment_dress", ModMaterials.MOON_FRAGMENT_DRESS_ARMOR, ArmorType.LEGGINGS);
    /** Same as the leggings in every way except the worn model: a pleated mini skirt. */
    public static final DeferredItem<Item> MOON_FRAGMENT_SKIRT = armor("moon_fragment_skirt", ModMaterials.MOON_FRAGMENT_SKIRT_ARMOR, ArmorType.LEGGINGS);
    public static final DeferredItem<Item> MOON_FRAGMENT_BOOTS = armor("moon_fragment_boots", MOON_FRAGMENT_ARMOR, ArmorType.BOOTS);

    /** Creative tab order. */
    public static List<DeferredItem<? extends Item>> all() {
        return List.of(MOON_FRAGMENT,
                MOON_FRAGMENT_SWORD, MOON_FRAGMENT_SPEAR, MOON_FRAGMENT_SHOVEL, MOON_FRAGMENT_PICKAXE, MOON_FRAGMENT_AXE, MOON_FRAGMENT_HOE,
                MOON_FRAGMENT_HELMET, MOON_FRAGMENT_CHESTPLATE, MOON_FRAGMENT_LEGGINGS, MOON_FRAGMENT_DRESS, MOON_FRAGMENT_SKIRT, MOON_FRAGMENT_BOOTS);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    /** A plain {@link Item} with the given properties, made fire resistant. */
    private static DeferredItem<Item> item(String name, UnaryOperator<Item.Properties> properties) {
        return ITEMS.registerItem(name, Item::new, p -> properties.apply(p).fireResistant());
    }

    /** An item of a custom class (axe, shovel, hoe), made fire resistant. */
    private static <I extends Item> DeferredItem<I> toolItem(String name, Function<Item.Properties, I> factory) {
        return ITEMS.registerItem(name, factory, Item.Properties::fireResistant);
    }

    private static DeferredItem<Item> armor(String name, ArmorMaterial material, ArmorType type) {
        return item(name, p -> p.humanoidArmor(material, type));
    }

    private ModItems() {
    }
}
