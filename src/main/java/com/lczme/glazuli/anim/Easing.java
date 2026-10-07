package com.lczme.glazuli.anim;

/** Maps linear progress in [0, 1] to eased progress. Results may overshoot [0, 1] (e.g. {@link #OUT_BACK}). */
@FunctionalInterface
public interface Easing {
    Easing LINEAR = t -> t;
    Easing IN_CUBIC = t -> t * t * t;
    Easing OUT_CUBIC = t -> {
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    };
    Easing IN_OUT_CUBIC = t -> {
        if (t < 0.5F) {
            return 4.0F * t * t * t;
        }
        float u = -2.0F * t + 2.0F;
        return 1.0F - u * u * u / 2.0F;
    };
    Easing OUT_QUINT = t -> {
        float u = 1.0F - t;
        return 1.0F - u * u * u * u * u;
    };
    Easing OUT_EXPO = t -> t >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, -10.0 * t);
    Easing IN_OUT_EXPO = t -> {
        if (t <= 0.0F) {
            return 0.0F;
        }
        if (t >= 1.0F) {
            return 1.0F;
        }
        return t < 0.5F
                ? (float) Math.pow(2.0, 20.0 * t - 10.0) / 2.0F
                : (2.0F - (float) Math.pow(2.0, -20.0 * t + 10.0)) / 2.0F;
    };
    Easing OUT_BACK = t -> {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float u = t - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    };

    /** Design spec {@code --ease}: {@code cubic-bezier(.32, .72, 0, 1)}, for page state changes (~0.35-0.40 s). */
    Easing SPEC_EASE = cubicBezier(0.32F, 0.72F, 0.0F, 1.0F);

    float apply(float t);

    /** CSS-style cubic Bézier timing function through (0,0), (x1,y1), (x2,y2), (1,1). */
    static Easing cubicBezier(float x1, float y1, float x2, float y2) {
        return t -> {
            if (t <= 0.0F || t >= 1.0F) {
                return t <= 0.0F ? 0.0F : 1.0F;
            }
            // Solve x(s) = t for the curve parameter s (Newton, then bisection fallback), return y(s).
            float s = t;
            for (int i = 0; i < 8; i++) {
                float x = bezier(s, x1, x2) - t;
                float dx = bezierSlope(s, x1, x2);
                if (Math.abs(x) < 1.0E-5F) {
                    return bezier(s, y1, y2);
                }
                if (Math.abs(dx) < 1.0E-6F) {
                    break;
                }
                s -= x / dx;
            }
            float lo = 0.0F;
            float hi = 1.0F;
            s = t;
            for (int i = 0; i < 20; i++) {
                float x = bezier(s, x1, x2);
                if (Math.abs(x - t) < 1.0E-5F) {
                    break;
                }
                if (x < t) {
                    lo = s;
                } else {
                    hi = s;
                }
                s = (lo + hi) * 0.5F;
            }
            return bezier(s, y1, y2);
        };
    }

    private static float bezier(float s, float p1, float p2) {
        float u = 1.0F - s;
        return 3.0F * u * u * s * p1 + 3.0F * u * s * s * p2 + s * s * s;
    }

    private static float bezierSlope(float s, float p1, float p2) {
        float u = 1.0F - s;
        return 3.0F * u * u * p1 + 6.0F * u * s * (p2 - p1) + 3.0F * s * s * (1.0F - p2);
    }
}
