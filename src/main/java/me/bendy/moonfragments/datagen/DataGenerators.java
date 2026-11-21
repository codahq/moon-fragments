package me.bendy.moonfragments.datagen;

import me.bendy.moonfragments.MoonFragments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Data generation (#5): recipes and tags are generated into {@code src/generated/resources} by {@code ./gradlew runData}.
 * Run it after changing any of the providers, and commit the generated files.
 * <p>
 * Still hand-written in {@code src/main/resources}: item models, client item definitions, equipment assets, lang, the
 * LambDynamicLights JSON, the two enchantments and their tags (data-driven enchantments aren't in the datagen registry
 * lookup).
 */
@EventBusSubscriber(modid = MoonFragments.MODID)
public final class DataGenerators {
    @SubscribeEvent
    static void gatherData(GatherDataEvent.Client event) {
        event.createProvider(ModRecipeProvider.Runner::new);
        event.createProvider(ModBlockTagsProvider::new);
        event.createProvider(ModItemTagsProvider::new);
        event.createProvider(ModEntityTypeTagsProvider::new);
    }

    private DataGenerators() {
    }
}
