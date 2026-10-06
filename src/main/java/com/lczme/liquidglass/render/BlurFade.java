package com.lczme.liquidglass.render;

import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.config.LiquidGlassConfig;

/**
 * Eases the menu background blur in when a blurred screen opens, and back out on the title screen (the one menu
 * that keeps the cave sharp), instead of switching between sharp and blurred in one frame.
 */
public final class BlurFade {
    private static final double SECONDS = 0.6;

    private static float level;
    private static double lastTime = Double.NaN;
    private static boolean requested;
    private static boolean requestedLastFrame;
    private static boolean fadeOutPass;

    private BlurFade() {
    }

    public static void onFrameStart() {
        double now = AnimClock.now();
        double dt = Double.isNaN(lastTime) ? 0.0 : Math.min(0.1, now - lastTime);
        lastTime = now;
        requestedLastFrame = requested;
        requested = false;
        float step = (float) (dt / SECONDS);
        level = requestedLastFrame ? Math.min(1.0F, level + step) : Math.max(0.0F, level - step);
    }

    /** Scales vanilla's blur radius for this frame; every call except the fade-out pass keeps the blur coming in. */
    public static float radius(float configured) {
        if (LiquidGlassConfig.isClassic()) {
            return configured;
        }
        if (!fadeOutPass) {
            requested = true;
        }
        return configured * Easing.IN_OUT_CUBIC.apply(level);
    }

    /** True while the blur of a screen that just closed is still fading out. */
    public static boolean isFadingOut() {
        return level > 0.0F && !requestedLastFrame && !LiquidGlassConfig.isClassic();
    }

    public static void fadeOut(Runnable blur) {
        fadeOutPass = true;
        try {
            blur.run();
        } finally {
            fadeOutPass = false;
        }
    }
}
