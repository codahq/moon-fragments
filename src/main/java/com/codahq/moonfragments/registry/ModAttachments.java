package com.codahq.moonfragments.registry;

import java.util.function.Supplier;

import com.codahq.moonfragments.MoonFragments;
import com.codahq.moonfragments.spawn.MoonShowers;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** NeoForge data attachments. */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MoonFragments.MODID);

    /**
     * Per-level shower bookkeeping (last night rolled, showers in progress). Not serialized: by design (#4) nothing is
     * saved, so a restart can re-roll the current night.
     */
    public static final Supplier<AttachmentType<MoonShowers.State>> SHOWER_STATE = ATTACHMENT_TYPES.register("shower_state",
            () -> AttachmentType.builder(MoonShowers.State::new).build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    private ModAttachments() {
    }
}
