package com.lczme.liquidglass.render;

/**
 * Immutable look of a glass surface, following the Liquid Glass design spec ({@code D:\ds\demo\design-spec.md}).
 * Lengths are in GUI units (before GUI scale and PoseStack scale).
 * <p>
 * Two materials (spec §1): {@link Material#GLASS} refracts the backdrop by the shape's distance field (§3) and is
 * used for small interactive controls; {@link Material#FILM} is a uniform translucent body for large surfaces.
 * Edges are expressed only by refraction and the anti-aliased mask; there is no rim highlight (spec removed it).
 * <p>
 * Platform note (§13): the §3.1-C colour tokens belong to the light Web landing page. Minecraft draws white text on
 * dark scenes, so films here use a dark body colour; the optical values (§3.1-A) are unchanged.
 *
 * @param material     glass (refracting control) or film (large translucent surface)
 * @param cornerRadius corner radius, clamped to half the shorter side ({@link #CAPSULE} for pills)
 * @param color        RGB the body mixes towards: the accent for glass (white = neutral), the body colour for film
 * @param tint         glass: colour mix amount (§3.1-B levels); film: concentration input, see {@link #bodyOpacity()}
 * @param shift        edge magnification, {@code k = 1 + shift} at the rim
 * @param falloff      exponent of the refraction falloff towards the centre
 * @param rimFrac      fraction of the half height that refracts
 * @param shadow       drop shadow opacity outside the shape
 * @param opacity      overall opacity multiplier, for fades
 * @param blur         film only: sample the blurred backdrop (spec §1 allows a light blur on films)
 */
public record GlassStyle(
        Material material,
        float cornerRadius,
        int color,
        float tint,
        float shift,
        float falloff,
        float rimFrac,
        float shadow,
        float opacity,
        boolean blur
) {
    // Spec §3.1-A / §3.1-B locked values.
    public static final float SHIFT = 0.37F;
    public static final float FALLOFF = 1.9F;
    public static final float RIM_FRAC = 1.0F;
    public static final float TINT_NEUTRAL = 0.37F;
    public static final float TINT_SECONDARY = 0.45F;
    public static final float TINT_PRIMARY = 0.72F;

    public static final float CAPSULE = 9999.0F;
    public static final float RADIUS = 12.0F;
    public static final int NEUTRAL_ACCENT = 0xFFFFFF;
    public static final int FILM_COLOR = 0x14161C;

    /** Fully transparent refracting glass: the only button look. */
    public static final GlassStyle CLEAR = glass(0.0F, NEUTRAL_ACCENT);
    /** Neutral glass level (§3.1-B). */
    public static final GlassStyle NEUTRAL = glass(TINT_NEUTRAL, NEUTRAL_ACCENT);
    /** Card / panel film with the default concentration (body 0.679). */
    public static final GlassStyle FILM = film(FILM_COLOR, TINT_NEUTRAL);

    public static GlassStyle glass(float tint, int accent) {
        return new GlassStyle(Material.GLASS, CAPSULE, accent, tint, SHIFT, FALLOFF, RIM_FRAC, 0.0F, 1.0F, false);
    }

    public static GlassStyle film(int color, float tint) {
        return new GlassStyle(Material.FILM, RADIUS, color, tint, 0.0F, FALLOFF, RIM_FRAC, 0.10F, 1.0F, true);
    }

    /** Spec §3: film opacity derived from tint, never tint itself. */
    public float bodyOpacity() {
        return Math.min(0.9F, 0.42F + tint * 0.7F);
    }

    public GlassStyle withCornerRadius(float cornerRadius) {
        return new GlassStyle(material, cornerRadius, color, tint, shift, falloff, rimFrac, shadow, opacity, blur);
    }

    public GlassStyle withShadow(float shadow) {
        return new GlassStyle(material, cornerRadius, color, tint, shift, falloff, rimFrac, shadow, opacity, blur);
    }

    public GlassStyle withOpacity(float opacity) {
        return new GlassStyle(material, cornerRadius, color, tint, shift, falloff, rimFrac, shadow, opacity, blur);
    }

    public enum Material {
        GLASS,
        FILM
    }
}
