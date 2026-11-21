package me.bendy.moonfragments.datagen;

import java.util.concurrent.CompletableFuture;

import me.bendy.moonfragments.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * Crafting recipes (issue #2): vanilla shapes with the moon fragment in place of the ingot. The dress and skirt use
 * dress-/skirt-shaped 7-fragment patterns because the leggings pattern is taken. Every recipe unlocks in the recipe
 * book when the player first holds a moon fragment.
 */
public class ModRecipeProvider extends RecipeProvider {
    protected ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        // Tools and weapons (X = fragment, # = stick)
        tool(ModItems.MOON_FRAGMENT_SWORD.get(), RecipeCategory.COMBAT, "X", "X", "#");
        tool(ModItems.MOON_FRAGMENT_SPEAR.get(), RecipeCategory.COMBAT, "  X", " # ", "#  ");
        tool(ModItems.MOON_FRAGMENT_SHOVEL.get(), RecipeCategory.TOOLS, "X", "#", "#");
        tool(ModItems.MOON_FRAGMENT_PICKAXE.get(), RecipeCategory.TOOLS, "XXX", " # ", " # ");
        tool(ModItems.MOON_FRAGMENT_AXE.get(), RecipeCategory.TOOLS, "XX", "X#", " #");
        tool(ModItems.MOON_FRAGMENT_HOE.get(), RecipeCategory.TOOLS, "XX", " #", " #");

        // Armor (X = fragment)
        armor(ModItems.MOON_FRAGMENT_HELMET.get(), "XXX", "X X");
        armor(ModItems.MOON_FRAGMENT_CHESTPLATE.get(), "X X", "XXX", "XXX");
        armor(ModItems.MOON_FRAGMENT_LEGGINGS.get(), "XXX", "X X", "X X");
        armor(ModItems.MOON_FRAGMENT_DRESS.get(), " X ", "XXX", "XXX");
        armor(ModItems.MOON_FRAGMENT_SKIRT.get(), "XXX", "XXX", " X ");
        armor(ModItems.MOON_FRAGMENT_BOOTS.get(), "X X", "X X");
    }

    private void tool(Item result, RecipeCategory category, String... pattern) {
        var builder = shaped(category, result).define('X', fragment()).define('#', Items.STICK);
        for (String row : pattern) {
            builder.pattern(row);
        }
        builder.unlockedBy("has_moon_fragment", has(fragment())).save(output);
    }

    private void armor(Item result, String... pattern) {
        var builder = shaped(RecipeCategory.COMBAT, result).define('X', fragment());
        for (String row : pattern) {
            builder.pattern(row);
        }
        builder.unlockedBy("has_moon_fragment", has(fragment())).save(output);
    }

    private static ItemLike fragment() {
        return ModItems.MOON_FRAGMENT.get();
    }

    public static class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new ModRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "Moon Fragments recipes";
        }
    }
}
