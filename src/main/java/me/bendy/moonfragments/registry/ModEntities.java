package me.bendy.moonfragments.registry;

import me.bendy.moonfragments.MoonFragments;
import me.bendy.moonfragments.entity.FallingMoonFragment;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(MoonFragments.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<FallingMoonFragment>> FALLING_MOON_FRAGMENT =
            ENTITIES.registerEntityType("falling_moon_fragment", FallingMoonFragment::new, MobCategory.MISC,
                    builder -> builder
                            .sized(0.5F, 0.5F)
                            .fireImmune()
                            .noLootTable()
                            // Tracked from far away (in chunks) so the beacon can be seen across the landscape.
                            .clientTrackingRange(16)
                            .updateInterval(2));

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
    }

    /** The fragment is a {@code LivingEntity} (for minimap icons), and every living entity needs attributes. */
    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(FALLING_MOON_FRAGMENT.get(), FallingMoonFragment.createAttributes().build());
    }

    private ModEntities() {
    }
}
