package com.lczme.glazuli.anim;

import net.minecraft.util.Mth;

/** A single time-based transition between two values. Starts on creation; query {@link #value()} every frame. */
public final class Tween {
    private final float from;
    private final float to;
    private final float duration;
    private final Easing easing;
    private double startTime;

    private Tween(float from, float to, float duration, Easing easing) {
        this.from = from;
        this.to = to;
        this.duration = duration;
        this.easing = easing;
        this.startTime = AnimClock.now();
    }

    public static Tween of(float from, float to, float seconds, Easing easing) {
        return new Tween(from, to, seconds, easing);
    }

    public Tween restart() {
        this.startTime = AnimClock.now();
        return this;
    }

    /** Linear progress in [0, 1]. */
    public float progress() {
        if (duration <= 0.0F) {
            return 1.0F;
        }
        return Mth.clamp((float) (AnimClock.now() - startTime) / duration, 0.0F, 1.0F);
    }

    public float value() {
        return Mth.lerp(easing.apply(progress()), from, to);
    }
}
