package com.lczme.liquidglass.anim;

/**
 * Damped spring (unit mass) chasing a target. Advances lazily on {@link #value()}, so it only needs to be queried
 * once per frame. Good for hover/press feedback where the target changes at any time.
 */
public final class Spring {
    private static final float MAX_STEP = 1.0F / 240.0F;
    // Caps catch-up after the spring wasn't queried for a while (screen hidden, lag spike).
    private static final float MAX_DELTA = 0.1F;
    private static final float REST_EPSILON = 0.001F;

    private final float stiffness;
    private final float damping;
    private float value;
    private float velocity;
    private float target;
    private double lastTime = Double.NaN;

    public Spring(float initial, float stiffness, float damping) {
        this.value = initial;
        this.target = initial;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    /** Critically damped: fast, no overshoot. */
    public static Spring smooth(float initial) {
        return new Spring(initial, 170.0F, 26.0F);
    }

    /** Under-damped: slight overshoot and settle, the "liquid" feel for hover and pop-in. */
    public static Spring bouncy(float initial) {
        return new Spring(initial, 320.0F, 20.0F);
    }

    public void setTarget(float target) {
        this.target = target;
    }

    public void setTarget(boolean on) {
        setTarget(on ? 1.0F : 0.0F);
    }

    public void snapTo(float value) {
        this.value = value;
        this.target = value;
        this.velocity = 0.0F;
    }

    public float target() {
        return target;
    }

    public float value() {
        double now = AnimClock.now();
        float dt = Double.isNaN(lastTime) ? 0.0F : (float) Math.min(now - lastTime, MAX_DELTA);
        lastTime = now;
        if (dt > 0.0F && !isSettled()) {
            int steps = (int) Math.ceil(dt / MAX_STEP);
            float h = dt / steps;
            for (int i = 0; i < steps; i++) {
                float accel = -stiffness * (value - target) - damping * velocity;
                velocity += accel * h;
                value += velocity * h;
            }
            if (isSettled()) {
                snapTo(target);
            }
        }
        return value;
    }

    public boolean isSettled() {
        return Math.abs(value - target) < REST_EPSILON && Math.abs(velocity) < REST_EPSILON;
    }
}
