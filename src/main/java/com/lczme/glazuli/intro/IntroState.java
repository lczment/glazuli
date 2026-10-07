package com.lczme.glazuli.intro;

import com.lczme.glazuli.anim.AnimClock;
import net.minecraft.util.Mth;

/** Public, render-thread-only view of the startup intro. */
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

    private static Phase phase = Phase.PENDING;
    private static double phaseStart = AnimClock.now();
    private static float outroDuration = 1.0F;
    private static boolean loadingDone;
    private static boolean skipRequested;

    private IntroState() {
    }

    public static Phase phase() {
        return phase;
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
}
