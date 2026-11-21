package com.codahq.moonfragments;

import com.codahq.moonfragments.client.FallingMoonFragmentRenderer;
import com.codahq.moonfragments.client.GarmentClientExtensions;
import com.codahq.moonfragments.client.GarmentModel;
import com.codahq.moonfragments.registry.ModEntities;
import com.codahq.moonfragments.registry.ModItems;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Client-only entry point. Not loaded on dedicated servers.
@Mod(value = MoonFragments.MODID, dist = Dist.CLIENT)
public class MoonFragmentsClient {
    public MoonFragmentsClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(MoonFragmentsClient::registerRenderers);
        modEventBus.addListener(MoonFragmentsClient::registerClientExtensions);
    }

    private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new GarmentClientExtensions(GarmentModel::createDress), ModItems.MOON_FRAGMENT_DRESS.get());
        event.registerItem(new GarmentClientExtensions(GarmentModel::createSkirt), ModItems.MOON_FRAGMENT_SKIRT.get());
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FALLING_MOON_FRAGMENT.get(), FallingMoonFragmentRenderer::new);
    }
}
