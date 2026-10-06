package com.lczme.liquidglass.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.config.LiquidGlassConfig.FontWeight;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Always-on hidden resource pack defining {@code liquidglass:ui}: HarmonyOS Sans, preceded by PingFang or a
 * user-supplied font when one is found (see {@link UserFont}), falling back to {@code minecraft:default}. Text in the
 * default font is redirected to it while it's in use, so classic mode switches without reloading. Generated rather
 * than static so the config can turn it off and so glyphs are rasterised at the current GUI scale.
 */
public final class FontPack implements PackResources {
    private static final String ID = "liquidglass/fonts";
    /** Font id that text in {@code minecraft:default} is redirected to while the UI font is in use. */
    public static final ResourceLocation UI_FONT_ID = LiquidGlass.id("ui");
    private static final ResourceLocation UI_FONT = LiquidGlass.id("font/ui.json");
    private static final ResourceLocation USER_FONT_FILE = LiquidGlass.id("font/user_font.ttf");
    private static final String USER_FONT = LiquidGlass.MODID + ":user_font.ttf";
    private static final String BUNDLED_FONT_PREFIX = LiquidGlass.MODID + ":harmonyos_sans_sc_";
    /** Em size in GUI pixels; CJK glyphs then fill the 9 px line like the vanilla unifont glyphs do. */
    private static final float SIZE = 10.0F;
    private static final float SHIFT_Y = 0.5F;

    private final PackLocationInfo location;
    @Nullable
    private FontWeight resolvedWeight;
    @Nullable
    private UserFont userFont;

    private FontPack(PackLocationInfo location) {
        this.location = location;
    }

    /** Re-resolved when the weight changes, since a weight change only rebuilds the fonts, not this pack. */
    @Nullable
    private synchronized UserFont userFont() {
        FontWeight weight = FontScale.weight();
        if (weight != this.resolvedWeight) {
            this.resolvedWeight = weight;
            this.userFont = UserFont.find(weight);
        }
        return this.userFont;
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        PackLocationInfo info = new PackLocationInfo(ID, Component.literal("Liquid Glass fonts"), PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new FontPack(location);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new FontPack(location);
            }
        };
        // Above vanilla and the mods' resources; not fixed, so resource packs enabled later are placed above it and their
        // fonts still win.
        Pack pack = Pack.readMetaAndCreate(info, resources, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack.hidden()));
        }
    }

    private byte[] fontDefinition() {
        int oversample = FontScale.oversampleForLoad();
        JsonArray providers = new JsonArray();
        if (this.userFont() != null) {
            providers.add(ttf(USER_FONT, oversample));
        }
        providers.add(ttf(BUNDLED_FONT_PREFIX + FontScale.weight().bundled() + ".ttf", oversample));
        // Glyphs the TTFs lack (resource packs' icon glyphs, symbols) come from the regular default font.
        JsonObject fallback = new JsonObject();
        fallback.addProperty("type", "reference");
        fallback.addProperty("id", ResourceLocation.withDefaultNamespace("default").toString());
        providers.add(fallback);
        JsonObject root = new JsonObject();
        root.add("providers", providers);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject ttf(String file, int oversample) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "ttf");
        provider.addProperty("file", file);
        provider.addProperty("size", SIZE);
        provider.addProperty("oversample", oversample);
        JsonArray shift = new JsonArray();
        shift.add(0.0F);
        shift.add(SHIFT_Y);
        provider.add("shift", shift);
        return provider;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES || !FontScale.isLoaded()) {
            return null;
        }
        if (location.equals(UI_FONT)) {
            return () -> new ByteArrayInputStream(this.fontDefinition());
        }
        UserFont userFont = location.equals(USER_FONT_FILE) ? this.userFont() : null;
        if (userFont != null) {
            return userFont::open;
        }
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type == PackType.CLIENT_RESOURCES && namespace.equals(LiquidGlass.MODID) && path.equals("font")
                && FontScale.isLoaded()) {
            output.accept(UI_FONT, () -> new ByteArrayInputStream(this.fontDefinition()));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(LiquidGlass.MODID) : Set.of();
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        if (deserializer == PackMetadataSection.TYPE) {
            return (T) new PackMetadataSection(Component.literal("HarmonyOS Sans / PingFang UI font"),
                    SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES), Optional.empty());
        }
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }

    @Override
    public void close() {
    }
}
