package com.lczme.liquidglass.font;

import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.lczme.liquidglass.config.LiquidGlassConfig.FontWeight;
import com.lczme.liquidglass.mixin.MinecraftAccessor;
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

    public static boolean isEnabled() {
        return !LiquidGlassConfig.SPEC.isLoaded() || LiquidGlassConfig.CUSTOM_FONT.get();
    }

    static FontWeight weight() {
        return LiquidGlassConfig.SPEC.isLoaded() ? LiquidGlassConfig.FONT_WEIGHT.get() : FontWeight.BOLD;
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
        if (isEnabled() && loadedScale != 0 && currentScale() != loadedScale) {
            reloadFonts();
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == LiquidGlassConfig.SPEC && (isEnabled() != lastEnabled || weight() != lastWeight)) {
            lastEnabled = isEnabled();
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
        ((MinecraftAccessor) mc).liquidglass$getFontManager()
                .reload(barrier, mc.getResourceManager(), InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Util.backgroundExecutor(), mc)
                .whenComplete((ignored, error) -> reloading = false);
    }
}
