package com.lczme.glazuli.intro;

import com.lczme.glazuli.anim.AnimClock;
import com.lczme.glazuli.anim.Easing;
import com.lczme.glazuli.anim.Tween;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Thin progress bar with a glowing leading edge and a stage label underneath. */
final class LoadingBar {
    private static final float HEIGHT = 3.0F;
    private static final int TRACK = 0x1EFFFFFF;
    private static final int FILL_LEFT = 0xFF4C78FF;
    private static final int FILL_RIGHT = 0xFFA6EAFF;
    private static final int EDGE_GLOW = 0xFFA6EAFF;
    private static final int LABEL = 0xFF8C95A8;
    private static final float LABEL_SIZE = 7.0F;
    // Rates for exponential smoothing; faster once done so the bar visibly completes before the outro.
    private static final float RATE_LOADING = 3.0F;
    private static final float RATE_DONE = 10.0F;

    private float shown;
    private double lastUpdate = Double.NaN;
    private String label = "";
    private final Tween labelFade = Tween.of(0.0F, 1.0F, 0.25F, Easing.OUT_CUBIC);

    /** Advances the smoothed value towards {@code actual}. Never moves backwards. */
    float update(float actual, boolean done) {
        double now = AnimClock.now();
        float dt = Double.isNaN(lastUpdate) ? 0.0F : (float) Math.min(now - lastUpdate, 0.1);
        lastUpdate = now;
        float target = done ? 1.0F : Mth.clamp(actual, 0.0F, 1.0F);
        float k = 1.0F - (float) Math.exp(-(done ? RATE_DONE : RATE_LOADING) * dt);
        shown = Math.max(shown, shown + (target - shown) * k);
        if (done && shown > 0.998F) {
            shown = 1.0F;
        }
        return shown;
    }

    float value() {
        return shown;
    }

    void setLabel(String text) {
        if (!text.equals(label)) {
            label = text;
            labelFade.restart();
        }
    }

    static float height() {
        return HEIGHT;
    }

    /** Draws the bar centered on {@code cx} with its top at {@code y}. */
    void draw(Matrix4f pose, float cx, float y, float width, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        float x = cx - width * 0.5F;
        float fill = Math.max(HEIGHT, width * shown);
        IntroDraw.begin();
        IntroDraw.roundedRect(pose, x, y, width, HEIGHT, HEIGHT * 0.5F, IntroDraw.withAlpha(TRACK, alpha), IntroDraw.withAlpha(TRACK, alpha));
        int right = IntroDraw.withAlpha(FILL_RIGHT, alpha);
        int left = IntroDraw.withAlpha(FILL_LEFT, alpha);
        IntroDraw.roundedRect(pose, x, y, fill, HEIGHT, HEIGHT * 0.5F, left, right);

        float pulse = 0.8F + 0.2F * Mth.sin((float) (AnimClock.now() * 4.0));
        IntroDraw.blendAdditive();
        IntroDraw.glow(pose, x + fill - 1.0F, y + HEIGHT * 0.5F, 11.0F, 5.0F, IntroDraw.withAlpha(EDGE_GLOW, alpha * 0.6F * pulse));
        IntroDraw.glow(pose, x + fill - 1.0F, y + HEIGHT * 0.5F, 3.5F, 2.2F, IntroDraw.withAlpha(0xFFFFFFFF, alpha * 0.7F));

        IntroDraw.blendPremultiplied();
        IntroText.draw(pose, label, cx, y + HEIGHT + 7.0F, LABEL_SIZE, IntroRaster.Weight.REGULAR,
                IntroDraw.withAlpha(LABEL, alpha * labelFade.value()), 0.5F);
        IntroDraw.end();
    }
}
