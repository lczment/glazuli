package com.lczme.liquidglass.render;

/**
 * Immutable look of a glass surface. Lengths are in GUI units (before GUI scale and PoseStack scale).
 * <p>
 * Two materials: {@link Material#GLASS} refracts the backdrop by the shape's distance field and is used for
 * controls; {@link Material#FILM} is a uniform translucent body for large surfaces. The refraction model and the film
 * values follow the Liquid Glass design spec ({@code D:\ds\demo\design-spec.md}); buttons use the iOS 26 standard glass
 * button look instead ({@link #button}): frosted backdrop, edge lensing, a specular rim lit from the top left and a soft
 * shadow, brightening on hover.
 * <p>
 * Platform note (spec §13): the §3.1-C colour tokens belong to the light Web landing page. Minecraft draws white text on
 * dark scenes, so films here use a dark body colour.
 *
 * @param material     glass (refracting control) or film (large translucent surface)
 * @param cornerRadius corner radius, clamped to half the shorter side ({@link #CAPSULE} for pills)
 * @param color        RGB the body mixes towards: the accent for glass (white = neutral), the body colour for film
 * @param tint         glass: colour mix amount; film: concentration input, see {@link #bodyOpacity()}
 * @param shift        edge magnification, {@code k = 1 + shift} at the rim
 * @param falloff      exponent of the refraction falloff towards the centre
 * @param rimFrac      fraction of the half height that refracts
 * @param shadow       drop shadow opacity outside the shape
 * @param opacity      overall opacity multiplier, for fades
 * @param blur         sample the blurred (frosted) backdrop instead of the sharp one
 * @param specular     glass only: strength of the specular rim and fill light (0 = spec look, 1 = iOS 26 button)
 * @param hover        glass only: pointer hover amount 0..1, brightens the body and the rim
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
        boolean blur,
        float specular,
        float hover
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

    /** Fully transparent refracting glass. */
    public static final GlassStyle CLEAR = glass(0.0F, NEUTRAL_ACCENT);
    /** Neutral glass level (§3.1-B). */
    public static final GlassStyle NEUTRAL = glass(TINT_NEUTRAL, NEUTRAL_ACCENT);
    /** Card / panel film with the default concentration (body 0.679). */
    public static final GlassStyle FILM = film(FILM_COLOR, TINT_NEUTRAL);

    public static GlassStyle glass(float tint, int accent) {
        return new GlassStyle(Material.GLASS, CAPSULE, accent, tint, SHIFT, FALLOFF, RIM_FRAC, 0.0F, 1.0F, false, 0.0F, 0.0F);
    }

    /**
     * iOS 26 standard glass button: frosted backdrop with the lensing concentrated in a band along the edge, a
     * light white tint, specular rim and a soft shadow.
     */
    public static GlassStyle button(float cornerRadius, float tint) {
        return new GlassStyle(Material.GLASS, cornerRadius, NEUTRAL_ACCENT, tint, 0.5F, 2.2F, 0.7F, 0.14F, 1.0F, true, 1.0F, 0.0F);
    }

    public static GlassStyle film(int color, float tint) {
        return new GlassStyle(Material.FILM, RADIUS, color, tint, 0.0F, FALLOFF, RIM_FRAC, 0.10F, 1.0F, true, 0.0F, 0.0F);
    }

    /** Spec §3: film opacity derived from tint, never tint itself. */
    public float bodyOpacity() {
        return Math.min(0.9F, 0.42F + tint * 0.7F);
    }

    public GlassStyle withCornerRadius(float cornerRadius) {
        return new GlassStyle(material, cornerRadius, color, tint, shift, falloff, rimFrac, shadow, opacity, blur, specular, hover);
    }

    public GlassStyle withShadow(float shadow) {
        return new GlassStyle(material, cornerRadius, color, tint, shift, falloff, rimFrac, shadow, opacity, blur, specular, hover);
    }

    public GlassStyle withOpacity(float opacity) {
        return new GlassStyle(material, cornerRadius, color, tint, shift, falloff, rimFrac, shadow, opacity, blur, specular, hover);
    }

    public GlassStyle withHover(float hover) {
        return hover == this.hover ? this
                : new GlassStyle(material, cornerRadius, color, tint, shift, falloff, rimFrac, shadow, opacity, blur, specular, hover);
    }

    public enum Material {
        GLASS,
        FILM
    }
}
