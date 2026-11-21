package me.bendy.moonfragments.registry;

import me.bendy.moonfragments.MoonFragments;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MoonFragments.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MOON_FRAGMENTS = TABS.register("moon_fragments",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.moonfragments"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.MOON_FRAGMENT.get().getDefaultInstance())
                    .displayItems((params, output) -> ModItems.all().forEach(item -> output.accept(item.get())))
                    .build());

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }

    private ModCreativeTabs() {
    }
}
