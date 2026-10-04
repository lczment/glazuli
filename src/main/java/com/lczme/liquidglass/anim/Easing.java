package com.lczme.liquidglass.anim;

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

    float apply(float t);
}
