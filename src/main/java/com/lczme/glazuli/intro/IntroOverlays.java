package com.lczme.glazuli.intro;

import com.lczme.glazuli.Glazuli;
import com.lczme.glazuli.config.GlazuliConfig;
import com.lczme.glazuli.mixin.LoadingOverlayAccessor;
import com.lczme.glazuli.mixin.NeoForgeLoadingOverlayAccessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay;

/**
 * Decides whether a loading overlay passed to {@link Minecraft#setOverlay} is swapped for ours.
 * <p>
 * Swapping the instance (rather than mixing into the two different {@code render} implementations) gives one code
 * path for both the initial {@link NeoForgeLoadingOverlay} and later vanilla {@link LoadingOverlay}s, keeps every
 * {@code instanceof LoadingOverlay} check working, and leaves overlays untouched when disabled. Overlays of any other
 * class (e.g. another mod's subclass) are never replaced.
 */
public final class IntroOverlays {
    private static final String PLAYED_MARKER = "intro_played";

    private IntroOverlays() {
    }

    @Nullable
    public static Overlay replace(Minecraft minecraft, @Nullable Overlay overlay) {
        if (!(overlay instanceof LoadingOverlay loading) || overlay instanceof GlassLoadingOverlay) {
            return overlay;
        }
        boolean neoForge = overlay.getClass() == NeoForgeLoadingOverlay.class;
        if (!neoForge && overlay.getClass() != LoadingOverlay.class) {
            return overlay;
        }
        boolean startup = IntroState.phase() == IntroState.Phase.PENDING && !minecraft.isGameLoadFinished();
        try {
            LoadingOverlayAccessor access = (LoadingOverlayAccessor) loading;
            if (startup) {
                if (!shouldPlayIntro()) {
                    IntroState.setPhase(IntroState.Phase.DISABLED);
                    return overlay;
                }
                EarlyWindowHandoff early = null;
                ProgressMeter meter = null;
                if (neoForge) {
                    NeoForgeLoadingOverlayAccessor neo = (NeoForgeLoadingOverlayAccessor) overlay;
                    early = new EarlyWindowHandoff(neo.glazuli$getDisplayWindow());
                    meter = neo.glazuli$getProgressMeter();
                }
                StartupOverlay replacement = new StartupOverlay(minecraft, access.glazuli$getReload(),
                        access.glazuli$getOnFinish(), access.glazuli$getFadeIn(), early, meter);
                IntroState.setPhase(IntroState.Phase.LOADING);
                return replacement;
            }
            if (GlazuliConfig.SPEC.isLoaded() && GlazuliConfig.STYLE_RELOAD_OVERLAY.get()) {
                return new ReloadOverlay(minecraft, access.glazuli$getReload(), access.glazuli$getOnFinish(),
                        access.glazuli$getFadeIn());
            }
        } catch (Throwable t) {
            Glazuli.LOGGER.error("Failed to replace the loading overlay, keeping the default one", t);
            if (startup) {
                IntroState.setPhase(IntroState.Phase.DISABLED);
            }
        }
        return overlay;
    }

    private static boolean shouldPlayIntro() {
        // Client configs are loaded during mod construction, before Minecraft creates the startup overlay.
        if (!GlazuliConfig.SPEC.isLoaded()) {
            return false;
        }
        return switch (GlazuliConfig.INTRO_MODE.get()) {
            case ALWAYS -> true;
            case FIRST_LAUNCH_ONLY -> !Files.exists(markerPath());
            case OFF -> false;
        };
    }

    /** Called once the startup intro handed off to the first screen. */
    static void onIntroPlayed(Minecraft minecraft) {
        if (GlazuliConfig.INTRO_MODE.get() == GlazuliConfig.IntroMode.FIRST_LAUNCH_ONLY) {
            try {
                Path marker = markerPath();
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, Instant.now().toString());
            } catch (IOException e) {
                Glazuli.LOGGER.warn("Could not persist that the intro was shown", e);
            }
        }
    }

    private static Path markerPath() {
        return FMLPaths.CONFIGDIR.get().resolve(Glazuli.MODID).resolve(PLAYED_MARKER);
    }
}
