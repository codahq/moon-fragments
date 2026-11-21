package me.bendy.moonfragments.datagen;

import java.util.concurrent.CompletableFuture;

import me.bendy.moonfragments.MoonFragments;
import me.bendy.moonfragments.registry.ModItems;
import me.bendy.moonfragments.registry.ModMaterials;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.ItemTags;
import net.neoforged.neoforge.common.data.ItemTagsProvider;

/**
 * Item tags. Adding our gear to the vanilla tool and armor tags is what lets enchanting tables, anvils and
 * enchantments treat it like any other tool or armor.
 */
public class ModItemTagsProvider extends ItemTagsProvider {
    public ModItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, MoonFragments.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ModMaterials.REPAIRS_MOON_FRAGMENT_GEAR).add(ModItems.MOON_FRAGMENT.get());

        tag(ItemTags.SWORDS).add(ModItems.MOON_FRAGMENT_SWORD.get());
        tag(ItemTags.SPEARS).add(ModItems.MOON_FRAGMENT_SPEAR.get());
        tag(ItemTags.SHOVELS).add(ModItems.MOON_FRAGMENT_SHOVEL.get());
        tag(ItemTags.PICKAXES).add(ModItems.MOON_FRAGMENT_PICKAXE.get());
        tag(ItemTags.AXES).add(ModItems.MOON_FRAGMENT_AXE.get());
        tag(ItemTags.HOES).add(ModItems.MOON_FRAGMENT_HOE.get());

        tag(ItemTags.HEAD_ARMOR).add(ModItems.MOON_FRAGMENT_HELMET.get());
        tag(ItemTags.CHEST_ARMOR).add(ModItems.MOON_FRAGMENT_CHESTPLATE.get());
        tag(ItemTags.LEG_ARMOR).add(ModItems.MOON_FRAGMENT_LEGGINGS.get(), ModItems.MOON_FRAGMENT_DRESS.get(),
                ModItems.MOON_FRAGMENT_SKIRT.get());
        tag(ItemTags.FOOT_ARMOR).add(ModItems.MOON_FRAGMENT_BOOTS.get());
    }
}
