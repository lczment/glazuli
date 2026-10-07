package com.lczme.glazuli.render;

/** Implemented by vanilla's {@code TabManager}: a newly selected tab's widgets slide in from the side it was picked on. */
public interface TabSlide {
    float DISTANCE = 36.0F;
    float SECONDS = 0.34F;

    /** -1 when the next tab lies left of the current one, 1 when right. */
    void glazuli$setSlideDirection(int direction);

    /** Advances the slide; once per frame, before the tab's widgets are drawn. */
    void glazuli$animateSlide();
}
