package me.bendy.moonfragments.datagen;

import java.util.concurrent.CompletableFuture;

import me.bendy.moonfragments.MoonFragments;
import me.bendy.moonfragments.spawn.MoonFragmentSpawner;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

/** Block tags. */
public class ModBlockTagsProvider extends BlockTagsProvider {
    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, MoonFragments.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Natural world-gen terrain a falling fragment may land on (#1). Leaves, logs, water and built blocks are not here.
        tag(MoonFragmentSpawner.LANDING_SURFACES)
                .addTag(BlockTags.DIRT)
                .addTag(BlockTags.SAND)
                .addTag(BlockTags.BASE_STONE_OVERWORLD)
                .addTag(BlockTags.TERRACOTTA)
                .add(Blocks.GRAVEL, Blocks.CLAY, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.BLUE_ICE,
                        Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.COARSE_DIRT, Blocks.SUSPICIOUS_SAND,
                        Blocks.SUSPICIOUS_GRAVEL, Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK, Blocks.OBSIDIAN);
    }
}
