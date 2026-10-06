package com.lczme.liquidglass.render;

/**
 * Dark text on light glass, scoped to the places that draw it (tooltips, toasts, chat, Jade): white and grey turn dark
 * grey, light colours keep their hue but are darkened, and drop shadows are dropped. Nest with {@link #push}/{@link #pop}.
 */
public final class DarkText {
    private static final int MAX_NEUTRAL_CHROMA = 24;
    /** Neutral text from this luminance up maps onto DARKEST..LIGHTEST; darker text is already readable. */
    private static final float LIGHT_NEUTRAL = 0.55F;
    private static final int DARKEST = 0x1D;
    private static final int LIGHTEST = 0x50;
    /** Coloured text is scaled down to at most this luminance. */
    private static final float MAX_COLOURED = 0.42F;

    private static int depth;

    private DarkText() {
    }

    public static void push() {
        depth++;
    }

    public static void pop() {
        depth = Math.max(0, depth - 1);
    }

    public static boolean isActive() {
        return depth > 0;
    }

    /** Resets the scope each frame, so an exception inside a scoped renderer can't leave it on. */
    public static void reset() {
        depth = 0;
    }

    /** ARGB (or RGB) colour for text on light glass; returns {@code color} unchanged outside a scope. */
    public static int remap(int color) {
        if (depth == 0) {
            return color;
        }
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float luminance = (0.2126F * r + 0.7152F * g + 0.0722F * b) / 255.0F;
        int alpha = color & 0xFF000000;
        if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= MAX_NEUTRAL_CHROMA) {
            if (luminance < LIGHT_NEUTRAL) {
                return color;
            }
            int v = Math.round(DARKEST + (1.0F - luminance) / (1.0F - LIGHT_NEUTRAL) * (LIGHTEST - DARKEST));
            return alpha | v << 16 | v << 8 | v;
        }
        if (luminance <= MAX_COLOURED) {
            return color;
        }
        float scale = MAX_COLOURED / luminance;
        return alpha | Math.round(r * scale) << 16 | Math.round(g * scale) << 8 | Math.round(b * scale);
    }
}
