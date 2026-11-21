package me.bendy.moonfragments.datagen;

import java.util.concurrent.CompletableFuture;

import me.bendy.moonfragments.MoonFragments;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/** Entity type tags. */
public class ModEntityTypeTagsProvider extends EntityTypeTagsProvider {
    /**
     * Entities whose worn moon fragment armor glows with LambDynamicLights (#3); referenced from
     * {@code assets/moonfragments/dynamiclights/entity/armor_*.json}.
     */
    public static final TagKey<EntityType<?>> ARMOR_WEARERS =
            TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MoonFragments.MODID, "armor_wearers"));

    public ModEntityTypeTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, MoonFragments.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ARMOR_WEARERS).add(
                EntityType.PLAYER, EntityType.ARMOR_STAND,
                EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER,
                EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.WITHER_SKELETON,
                EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.ZOMBIFIED_PIGLIN);
    }
}
