package com.lczme.liquidglass.render;

import com.lczme.liquidglass.config.LiquidGlassConfig;
import net.minecraft.client.gui.GuiGraphics;

/** Glass look for vanilla buttons, applied by {@code AbstractButtonMixin} in place of the button sprite. */
public final class GlassButtons {
    private static final GlassStyle INACTIVE = GlassStyle.BUTTON.withTint(0x0CFFFFFF).withRim(0.2F).withShadow(0.1F).withOpacity(0.6F);

    private GlassButtons() {
    }

    public static boolean isEnabled() {
        return LiquidGlassConfig.SPEC.isLoaded() && LiquidGlassConfig.GLASS_BUTTONS.get();
    }

    /**
     * Draws the button background. The caller's {@code guiGraphics.setColor} alpha (widget fade) is applied by the
     * glass shader, so it is not folded into the style here.
     *
     * @param hover hover amount from a spring, may overshoot [0, 1]
     */
    public static void draw(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean active, float hover) {
        GlassStyle style = active ? GlassStyle.lerp(GlassStyle.BUTTON, GlassStyle.BUTTON_HOVER, hover) : INACTIVE;
        GlassRenderer.drawPanel(guiGraphics, x, y, width, height, style);
    }
}
