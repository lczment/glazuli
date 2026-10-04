package com.lczme.liquidglass.render;

/** Implemented on vanilla buttons by {@code AbstractButtonMixin}; queried once per frame by {@code AbstractWidgetMixin}. */
public interface GlassPressable {
    /** Advances the press spring and returns the current squash factor {@code s} (1 = at rest). */
    float liquidglass$pressScale();
}
