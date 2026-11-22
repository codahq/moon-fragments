package me.bendy.moonfragments.data;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import me.bendy.moonfragments.CommonConfig;
import me.bendy.moonfragments.MoonFragments;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * A built-in, always-on, hidden data pack generated from {@link CommonConfig}.
 *
 * <p>Shrinksh and Growge join the vanilla enchanting-table, loot, trade and mob-spawn tags through
 * {@code #minecraft:non_treasure}. For each spot that is turned off, this pack supplies that tag's file with a
 * {@code remove} list naming both enchantments. It sits at the top of the pack order, so its removals come after
 * the {@code #minecraft:non_treasure} reference has added them. The files are rebuilt from the config each time
 * the pack is opened, so {@code /reload} picks up config changes.
 */
public final class SizeEnchantmentPack {
    private static final String PACK_ID = MoonFragments.MODID + "/size_enchantment_config";
    private static final String TAGS_DIR = Registries.tagsDirPath(Registries.ENCHANTMENT);
    private static final List<String> SIZE_ENCHANTMENTS = List.of(
            MoonFragments.MODID + ":shrinksh",
            MoonFragments.MODID + ":growge");

    private SizeEnchantmentPack() {
    }

    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        PackLocationInfo location = new PackLocationInfo(PACK_ID,
                Component.literal("Moon Fragments enchantment config"), PackSource.BUILT_IN, Optional.empty());
        Pack.Metadata metadata = new Pack.Metadata(
                Component.literal("Keeps Shrinksh and Growge out of the spots turned off in moonfragments-common.toml"),
                PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of(), true);
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo info) {
                return new Resources(info);
            }

            @Override
            public PackResources openFull(PackLocationInfo info, Pack.Metadata meta) {
                return new Resources(info);
            }
        };
        Pack pack = new Pack(location, resources, metadata, new PackSelectionConfig(true, Pack.Position.TOP, false));
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    /** Tag files (path under {@code data/minecraft/}) for every spot turned off in the config right now. */
    private static Map<Identifier, byte[]> buildFiles() {
        Map<Identifier, byte[]> files = new LinkedHashMap<>();
        if (!CommonConfig.SPEC.isLoaded()) {
            return files;
        }
        String remove = "\"" + String.join("\", \"", SIZE_ENCHANTMENTS) + "\"";
        byte[] json = ("{ \"values\": [], \"remove\": [ " + remove + " ] }").getBytes(StandardCharsets.UTF_8);
        for (CommonConfig.SizeSpot spot : CommonConfig.SIZE_SPOTS) {
            if (!spot.enabled().getAsBoolean()) {
                Identifier tag = spot.tag().location();
                files.put(tag.withPath(TAGS_DIR + "/" + tag.getPath() + ".json"), json);
            }
        }
        return files;
    }

    private static final class Resources extends AbstractPackResources {
        private final Map<Identifier, byte[]> files = buildFiles();

        Resources(PackLocationInfo location) {
            super(location);
        }

        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
            return null;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
            byte[] bytes = type == PackType.SERVER_DATA ? this.files.get(location) : null;
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type != PackType.SERVER_DATA) {
                return;
            }
            String prefix = path.endsWith("/") ? path : path + "/";
            this.files.forEach((id, bytes) -> {
                if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
                    output.accept(id, () -> new ByteArrayInputStream(bytes));
                }
            });
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == PackType.SERVER_DATA ? Set.of(Identifier.DEFAULT_NAMESPACE) : Set.of();
        }

        @Override
        public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) {
            return null;
        }

        @Override
        public void close() {
        }
    }
}
