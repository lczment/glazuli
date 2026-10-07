package com.lczme.glazuli.render;

/** Implemented on vanilla buttons by {@code AbstractButtonMixin}; queried once per frame by {@code AbstractWidgetMixin}. */
public interface GlassPressable {
    /** Advances the press and hover springs and returns the current squash factor {@code s} (1 = at rest). */
    float glazuli$pressScale();

    /** Hover amount 0..1 as of the last {@link #glazuli$pressScale()} call. */
    float glazuli$hover();
}
