package com.lczme.glazuli.anim;

import net.minecraft.Util;

/** Monotonic wall-clock time shared by all animations, independent of game ticks and frame rate. */
public final class AnimClock {
    private static final long START = Util.getNanos();
    /** Past this the frame clock counts as stopped (frames aren't running yet, or one stalled) and live time is used. */
    private static final long STALE_NANOS = 250_000_000L;
    private static volatile long frameNanos;

    private AnimClock() {
    }

    /** Latches the time at the start of a frame. */
    public static void onFrameStart() {
        frameNanos = Util.getNanos();
    }

    /**
     * Seconds since class load, as of the start of the current frame. Double precision so long sessions don't lose
     * sub-millisecond accuracy. Everything drawn in a frame animates to the same instant, so motion advances by exactly
     * the frame interval however long the work before a given draw took.
     */
    public static double now() {
        long t = Util.getNanos();
        long frame = frameNanos;
        if (frame != 0L && t - frame < STALE_NANOS) {
            t = frame;
        }
        return (t - START) / 1.0E9;
    }
}
