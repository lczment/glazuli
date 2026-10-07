package com.lczme.glazuli.font;

import com.lczme.glazuli.config.GlazuliConfig;
import com.lczme.glazuli.config.GlazuliConfig.FontWeight;
import com.lczme.glazuli.mixin.MinecraftAccessor;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Font glyph textures are sampled with nearest filtering, so TrueType glyphs are only crisp when rasterised at exactly
 * one texel per screen pixel: the oversample factor follows the GUI scale, and the fonts are rebuilt when it changes.
 */
public final class FontScale {
    private static volatile int loadedScale;
    private static boolean reloading;
    private static boolean lastEnabled = true;
    private static FontWeight lastWeight = FontWeight.BOLD;

    private FontScale() {
    }

    /** Whether the UI font is loaded; it stays loaded in classic mode so switching back is instant. */
    public static boolean isLoaded() {
        return !GlazuliConfig.SPEC.isLoaded() || GlazuliConfig.CUSTOM_FONT.get();
    }

    /** Whether text uses the UI font instead of the pixel font. */
    public static boolean isEnabled() {
        return isLoaded() && !GlazuliConfig.isClassic();
    }

    static FontWeight weight() {
        return GlazuliConfig.SPEC.isLoaded() ? GlazuliConfig.FONT_WEIGHT.get() : FontWeight.BOLD;
    }

    static int oversampleForLoad() {
        int scale = currentScale();
        loadedScale = scale;
        return scale;
    }

    private static int currentScale() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.getWindow() == null ? 2 : Math.max(1, (int) Math.round(mc.getWindow().getGuiScale()));
    }

    /** Called after the window or GUI scale changed. */
    public static void onResize() {
        if (isLoaded() && loadedScale != 0 && currentScale() != loadedScale) {
            reloadFonts();
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == GlazuliConfig.SPEC) {
            refresh();
        }
    }

    /** Rebuilds the fonts if the font settings changed since they were last loaded. */
    private static void refresh() {
        if (isLoaded() != lastEnabled || weight() != lastWeight) {
            lastEnabled = isLoaded();
            lastWeight = weight();
            Minecraft.getInstance().execute(FontScale::reloadFonts);
        }
    }

    /** Rebuilds only the font manager rather than running a full resource reload. */
    private static void reloadFonts() {
        Minecraft mc = Minecraft.getInstance();
        if (reloading || mc.getOverlay() != null) {
            return;
        }
        reloading = true;
        PreparableReloadListener.PreparationBarrier barrier = new PreparableReloadListener.PreparationBarrier() {
            @Override
            public <T> CompletableFuture<T> wait(T backgroundResult) {
                return CompletableFuture.completedFuture(backgroundResult);
            }
        };
        ((MinecraftAccessor) mc).glazuli$getFontManager()
                .reload(barrier, mc.getResourceManager(), InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Util.backgroundExecutor(), mc)
                .whenComplete((ignored, error) -> reloading = false);
    }
}
