package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.anim.AnimClock;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;

/**
 * Public, render-thread-only view of the startup intro, for screens that continue its animation (e.g. a custom
 * title screen picking up the logo where the outro left it).
 */
public final class IntroState {
    public enum Phase {
        /** Startup overlay not created yet. */
        PENDING,
        /** Intro overlay is showing loading progress (including the short "bar fills up" step after loading). */
        LOADING,
        /** Loading finished; the intro is dissolving into the first screen, which is already set and rendering. */
        OUTRO,
        /** The intro played and handed off. */
        FINISHED,
        /** The intro does not play this session (config OFF, already shown, or the overlay could not be replaced). */
        DISABLED
    }

    /**
     * Logo placement in GUI coordinates.
     *
     * @param centerX logo center
     * @param centerY logo center
     * @param width   drawn width (already scaled)
     * @param height  drawn height (already scaled)
     * @param alpha   opacity in [0, 1]
     */
    public record LogoPose(float centerX, float centerY, float width, float height, float alpha) {
    }

    static final float MENU_LOGO_Y = 0.24F;
    static final float MENU_LOGO_SCALE = 0.82F;

    private static Phase phase = Phase.PENDING;
    private static double phaseStart = AnimClock.now();
    private static float outroDuration = 1.0F;
    @Nullable
    private static LogoPose logoPose;
    private static boolean logoHandoff;
    private static boolean loadingDone;
    private static boolean skipRequested;

    private IntroState() {
    }

    public static Phase phase() {
        return phase;
    }

    /** Vertical logo center where the outro leaves the logo, in GUI units. */
    public static float menuLogoCenterY(float screenHeight) {
        return screenHeight * MENU_LOGO_Y;
    }

    /** Logo box height where the outro leaves the logo, in GUI units. */
    public static float menuLogoHeight(float screenHeight) {
        return StartupOverlay.logoHeight(screenHeight) * MENU_LOGO_SCALE;
    }

    public static boolean isPlaying() {
        return phase == Phase.LOADING || phase == Phase.OUTRO;
    }

    public static boolean isOutroPlaying() {
        return phase == Phase.OUTRO;
    }
    /** {@code true} once nothing intro-related will be drawn anymore this session. */
    public static boolean isDone() {
        return phase == Phase.FINISHED || phase == Phase.DISABLED;
    }

    /** Seconds since the current phase started. */
    public static float phaseElapsed() {
        return (float) (AnimClock.now() - phaseStart);
    }

    /** Outro progress in [0, 1]: 0 before the outro, 1 once finished. */
    public static float outroProgress() {
        return switch (phase) {
            case PENDING, LOADING, DISABLED -> 0.0F;
            case OUTRO -> Mth.clamp(phaseElapsed() / outroDuration, 0.0F, 1.0F);
            case FINISHED -> 1.0F;
        };
    }

    /** Where the logo is drawn this frame, or where it ended once {@link Phase#FINISHED}. {@code null} if never drawn. */
    @Nullable
    public static LogoPose logoPose() {
        return logoPose;
    }

    /**
     * Asks the outro to keep the logo fully opaque at its end pose instead of fading it out, because the caller will
     * draw the same logo there from then on (see {@link IntroLogo#draw}). Must be called before the outro ends.
     */
    public static void requestLogoHandoff() {
        logoHandoff = true;
    }

    public static boolean isLogoHandoffRequested() {
        return logoHandoff;
    }

    /** Requests skipping the rest of the intro. Only possible once loading has finished. Returns whether it was accepted. */
    public static boolean requestSkip() {
        if (phase == Phase.OUTRO || phase == Phase.LOADING && loadingDone) {
            skipRequested = true;
            return true;
        }
        return false;
    }

    static boolean consumeSkip() {
        boolean skip = skipRequested;
        skipRequested = false;
        return skip;
    }

    static void setLoadingDone() {
        loadingDone = true;
    }

    static void setPhase(Phase newPhase) {
        phase = newPhase;
        phaseStart = AnimClock.now();
    }

    static void setOutroDuration(float seconds) {
        outroDuration = seconds;
    }

    static void setLogoPose(@Nullable LogoPose pose) {
        logoPose = pose;
    }
}
