package me.bendy.moonfragments;

import me.bendy.moonfragments.command.MoonFragmentsCommand;
import me.bendy.moonfragments.spawn.MoonFragmentSpawner;
import me.bendy.moonfragments.spawn.MoonShowers;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Game-bus (NeoForge.EVENT_BUS) listeners. */
@EventBusSubscriber(modid = MoonFragments.MODID)
public final class GameEvents {
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MoonFragmentSpawner.tick(level);
            MoonShowers.tick(level);
        }
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        MoonFragmentsCommand.register(event.getDispatcher());
    }

    private GameEvents() {
    }
}
