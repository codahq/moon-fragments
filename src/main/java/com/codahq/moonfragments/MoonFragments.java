package com.codahq.moonfragments;

import org.slf4j.Logger;

import com.codahq.moonfragments.registry.ModCreativeTabs;
import com.codahq.moonfragments.registry.ModEntities;
import com.codahq.moonfragments.registry.ModItems;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(MoonFragments.MODID)
public class MoonFragments {
    public static final String MODID = "moonfragments";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MoonFragments(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }
}
