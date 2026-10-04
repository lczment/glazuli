package com.lczme.liquidglass.render;

import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Immutable look of a glass panel. All lengths are in GUI units (before GUI scale and PoseStack scale).
 *
 * @param cornerRadius     corner radius, clamped to half the shorter side
 * @param tint             ARGB tint; alpha is how strongly the tint covers the backdrop
 * @param refraction       maximum backdrop displacement at the rim
 * @param bevel            width of the curved edge band in which refraction, fresnel and chromatic aberration happen
 * @param rim              strength of the rim highlight / fresnel / inner shadow
 * @param chromatic        channel separation at the rim
 * @param saturation       backdrop saturation multiplier (1 = unchanged)
 * @param shadow           drop shadow opacity outside the panel
 * @param opacity          overall opacity multiplier, for fades
 * @param blur             sample the blurred backdrop; {@code false} gives clear refracting glass
 */
public record GlassStyle(
        float cornerRadius,
        int tint,
        float refraction,
        float bevel,
        float rim,
        float chromatic,
        float saturation,
        float shadow,
        float opacity,
        boolean blur
) {
    public static final GlassStyle PANEL = new GlassStyle(14.0F, 0x1FFFFFFF, 7.0F, 14.0F, 0.55F, 1.4F, 1.3F, 0.35F, 1.0F, true);
    public static final GlassStyle DARK_PANEL = new GlassStyle(14.0F, 0x7A14161C, 7.0F, 14.0F, 0.5F, 1.4F, 1.2F, 0.45F, 1.0F, true);
    public static final GlassStyle BUTTON = new GlassStyle(10.0F, 0x1AFFFFFF, 4.0F, 8.0F, 0.5F, 0.9F, 1.25F, 0.25F, 1.0F, true);
    public static final GlassStyle BUTTON_HOVER = new GlassStyle(10.0F, 0x38FFFFFF, 4.5F, 8.0F, 0.85F, 1.0F, 1.35F, 0.3F, 1.0F, true);
    public static final GlassStyle LENS = new GlassStyle(1000.0F, 0x08FFFFFF, 10.0F, 18.0F, 0.6F, 1.6F, 1.1F, 0.3F, 1.0F, false);

    /** Blends every numeric property; {@code blur} switches at the halfway point. {@code t} may overshoot for springs. */
    public static GlassStyle lerp(GlassStyle a, GlassStyle b, float t) {
        return new GlassStyle(
                Mth.lerp(t, a.cornerRadius, b.cornerRadius),
                FastColor.ARGB32.lerp(Mth.clamp(t, 0.0F, 1.0F), a.tint, b.tint),
                Mth.lerp(t, a.refraction, b.refraction),
                Mth.lerp(t, a.bevel, b.bevel),
                Math.max(0.0F, Mth.lerp(t, a.rim, b.rim)),
                Math.max(0.0F, Mth.lerp(t, a.chromatic, b.chromatic)),
                Math.max(0.0F, Mth.lerp(t, a.saturation, b.saturation)),
                Math.max(0.0F, Mth.lerp(t, a.shadow, b.shadow)),
                Mth.lerp(t, a.opacity, b.opacity),
                t < 0.5F ? a.blur : b.blur);
    }

    public GlassStyle withCornerRadius(float cornerRadius) {
        return new GlassStyle(cornerRadius, tint, refraction, bevel, rim, chromatic, saturation, shadow, opacity, blur);
    }

    public GlassStyle withTint(int tint) {
        return new GlassStyle(cornerRadius, tint, refraction, bevel, rim, chromatic, saturation, shadow, opacity, blur);
    }

    public GlassStyle withRefraction(float refraction) {
        return new GlassStyle(cornerRadius, tint, refraction, bevel, rim, chromatic, saturation, shadow, opacity, blur);
    }

    public GlassStyle withRim(float rim) {
        return new GlassStyle(cornerRadius, tint, refraction, bevel, rim, chromatic, saturation, shadow, opacity, blur);
    }

    public GlassStyle withShadow(float shadow) {
        return new GlassStyle(cornerRadius, tint, refraction, bevel, rim, chromatic, saturation, shadow, opacity, blur);
    }

    public GlassStyle withOpacity(float opacity) {
        return new GlassStyle(cornerRadius, tint, refraction, bevel, rim, chromatic, saturation, shadow, opacity, blur);
    }

    public GlassStyle withBlur(boolean blur) {
        return new GlassStyle(cornerRadius, tint, refraction, bevel, rim, chromatic, saturation, shadow, opacity, blur);
    }
}
