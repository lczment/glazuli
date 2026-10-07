package com.lczme.glazuli.anim;

import net.minecraft.util.Mth;

/** A single time-based transition between two values. Starts on creation; query {@link #value()} every frame. */
public final class Tween {
    private float from;
    private float to;
    private float duration;
    private float delay;
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

    /** A tween that is already finished and holds {@code value}. Handy as an initial state before the first retarget. */
    public static Tween at(float value) {
        return new Tween(value, value, 0.0F, Easing.LINEAR);
    }

    public Tween delay(float seconds) {
        this.delay = seconds;
        return this;
    }

    public Tween restart() {
        this.startTime = AnimClock.now();
        return this;
    }

    /** Animates from the current value to {@code target}, so interrupting a running tween doesn't jump. */
    public void retarget(float target, float seconds) {
        if (target == to) {
            return;
        }
        this.from = value();
        this.to = target;
        this.duration = seconds;
        this.delay = 0.0F;
        restart();
    }

    /** Linear progress in [0, 1]. */
    public float progress() {
        if (duration <= 0.0F) {
            return AnimClock.now() - startTime >= delay ? 1.0F : 0.0F;
        }
        return Mth.clamp((float) (AnimClock.now() - startTime - delay) / duration, 0.0F, 1.0F);
    }

    public float value() {
        return Mth.lerp(easing.apply(progress()), from, to);
    }

    public float target() {
        return to;
    }

    public boolean isDone() {
        return progress() >= 1.0F;
    }
}
