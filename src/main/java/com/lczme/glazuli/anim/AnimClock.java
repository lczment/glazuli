package com.lczme.glazuli.anim;

import net.minecraft.Util;

/** Monotonic wall-clock time shared by all animations, independent of game ticks and frame rate. */
public final class AnimClock {
    private static final long START = Util.getNanos();

    private AnimClock() {
    }

    /** Seconds since class load. Double precision so long sessions don't lose sub-millisecond accuracy. */
    public static double now() {
        return (Util.getNanos() - START) / 1.0E9;
    }
}
