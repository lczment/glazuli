package com.lczme.liquidglass.render;

/** Implemented on vanilla buttons by {@code AbstractButtonMixin}; queried once per frame by {@code AbstractWidgetMixin}. */
public interface GlassPressable {
    /** Advances the press and hover springs and returns the current squash factor {@code s} (1 = at rest). */
    float liquidglass$pressScale();

    /** Hover amount 0..1 as of the last {@link #liquidglass$pressScale()} call. */
    float liquidglass$hover();
}
