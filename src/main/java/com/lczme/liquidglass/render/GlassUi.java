package com.lczme.liquidglass.render;

import com.lczme.liquidglass.anim.Spring;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/** Shared building blocks for restyled mod screens: cards, glass buttons, iOS switches and soft highlights. */
public final class GlassUi {
    public static final float CARD_RADIUS = 8.0F;
    public static final GlassStyle CARD = GlassStyle.film(GlassStyle.FILM_COLOR, 0.12F).withCornerRadius(CARD_RADIUS);
    public static final GlassStyle POPOVER = GlassStyle.film(GlassStyle.FILM_COLOR, 0.32F).withCornerRadius(6.0F);
    public static final GlassStyle SELECTION = GlassStyle.button(5.0F, 0.16F).withShadow(0.0F);
    /** Text starts 6 px in, so the corners stay tighter than a capsule. */
    public static final GlassStyle FIELD = GlassButtons.BUTTON_CLEAR.withCornerRadius(6.0F);
    public static final GlassStyle SWITCH_OFF = GlassStyle.button(GlassStyle.CAPSULE, 0.22F).withShadow(0.0F);
    public static final GlassStyle SWITCH_ON = new GlassStyle(GlassStyle.Material.GLASS, GlassStyle.CAPSULE, 0x34C759, 0.82F,
            0.5F, 2.2F, 0.7F, 0.0F, 1.0F, true, 1.0F, 0.0F);
    public static final GlassStyle KNOB = GlassStyle.button(GlassStyle.CAPSULE, 0.92F).withShadow(0.2F);
    public static final GlassStyle TRACK = GlassStyle.button(GlassStyle.CAPSULE, 0.2F).withShadow(0.0F);

    public static final int ROW = 0x0CFFFFFF;
    public static final int ROW_HOVER = 0x1EFFFFFF;
    public static final int ROW_ACTIVE = 0x2CFFFFFF;
    public static final int SCROLL_THUMB = 0x66FFFFFF;
    public static final int SCROLL_THUMB_ACTIVE = 0xAAFFFFFF;
    public static final int SECONDARY_TEXT = 0xFFB4B8C0;
    public static final float DISABLED_OPACITY = 0.45F;
    private static final int FLAT_SWITCH_OFF = 0x66787880;

    private GlassUi() {
    }

    public static Spring hoverSpring() {
        return Spring.of(GlassButtons.HOVER_RESPONSE, GlassButtons.HOVER_DAMPING, 0.0F);
    }

    public static float hover(Spring spring, boolean target) {
        spring.setTarget(target);
        return Mth.clamp(spring.value(), 0.0F, 1.0F);
    }

    public static void panel(GuiGraphics g, float x, float y, float width, float height, GlassStyle style) {
        GlassRenderer.drawPanel(g, x, y, width, height, style);
    }

    public static void button(GuiGraphics g, int x1, int y1, int x2, int y2, float hover, boolean enabled) {
        GlassStyle style = GlassButtons.BUTTON_CLEAR.withHover(hover);
        panel(g, x1, y1, x2 - x1, y2 - y1, enabled ? style : style.withOpacity(DISABLED_OPACITY));
    }

    /** iOS switch: capsule track that turns green, white knob sliding with {@code position} 0..1. */
    public static void toggle(GuiGraphics g, int x, int y, int width, int height, float position, float hover, boolean enabled) {
        float opacity = enabled ? 1.0F : DISABLED_OPACITY;
        float on = Mth.clamp(position, 0.0F, 1.0F);
        float inset = 1.0F;
        float knob = height - inset * 2.0F;
        float knobX = x + inset + (width - knob - inset * 2.0F) * position;
        if (!GlassRenderer.isGlassAvailable()) {
            // The flat fallback tints every glass body dark, which would hide the on/off state.
            int track = FastColor.ARGB32.lerp(on, FLAT_SWITCH_OFF, 0xFF000000 | SWITCH_ON.color());
            GlassRenderer.fillRounded(g, x, y, width, height, height * 0.5F, withAlpha(track, opacity));
            knob(g, knobX, y + inset, knob, 0.0F, opacity);
            return;
        }
        panel(g, x, y, width, height, SWITCH_OFF.withHover(hover).withOpacity(opacity * (1.0F - on)));
        panel(g, x, y, width, height, SWITCH_ON.withHover(hover).withOpacity(opacity * on));
        knob(g, knobX, y + inset, knob, 0.0F, opacity);
    }

    /** Round white slider/switch knob. */
    public static void knob(GuiGraphics g, float x, float y, float size, float hover, float opacity) {
        if (!GlassRenderer.isGlassAvailable()) {
            GlassRenderer.fillRounded(g, x, y, size, size, size * 0.5F, withAlpha(hover > 0.0F ? 0xFFFFFFFF : 0xFFE8E8E8, opacity));
            return;
        }
        panel(g, x, y, size, size, KNOB.withHover(hover).withOpacity(opacity));
    }

    /** Slim capsule groove for sliders. */
    public static void track(GuiGraphics g, float x, float y, float width, float height) {
        if (!GlassRenderer.isGlassAvailable()) {
            GlassRenderer.fillRounded(g, x, y, width, height, height * 0.5F, FLAT_SWITCH_OFF);
            return;
        }
        panel(g, x, y, width, height, TRACK);
    }

    private static int withAlpha(int argb, float opacity) {
        return FastColor.ARGB32.color(Math.round((argb >>> 24) * Mth.clamp(opacity, 0.0F, 1.0F)), argb);
    }

    /** Fill with pixel-rounded corners ({@code radius} 1..3 GUI pixels), crisp at any GUI scale. */
    public static void roundedFill(GuiGraphics g, int x1, int y1, int x2, int y2, int radius, int color) {
        if (x2 <= x1 || y2 <= y1 || (color >>> 24) == 0) {
            return;
        }
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        int[] insets = switch (r) {
            case 0 -> new int[0];
            case 1 -> new int[]{1};
            case 2 -> new int[]{2, 1};
            default -> new int[]{3, 1, 1};
        };
        int rows = insets.length;
        for (int i = 0; i < rows; i++) {
            g.fill(x1 + insets[i], y1 + i, x2 - insets[i], y1 + i + 1, color);
            g.fill(x1 + insets[i], y2 - i - 1, x2 - insets[i], y2 - i, color);
        }
        g.fill(x1, y1 + rows, x2, y2 - rows, color);
    }
}
