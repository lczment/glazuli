package com.lczme.liquidglass.render;

import com.lczme.liquidglass.config.LiquidGlassConfig;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Glass look for vanilla buttons, applied by {@code AbstractButtonMixin} in place of the button sprite.
 * Every button uses the same transparent glass ({@link GlassStyle#CLEAR}); state is shown by the press spring and
 * vanilla's label colour (grey when inactive), never by switching to another tint.
 */
public final class GlassButtons {
    /** Spec §6 button press: target scale and spring (response 0.28 s, damping ratio 0.68). */
    public static final float PRESSED_SCALE = 0.92F;
    public static final float PRESS_RESPONSE = 0.28F;
    public static final float PRESS_DAMPING = 0.68F;

    private GlassButtons() {
    }

    public static boolean isEnabled() {
        return LiquidGlassConfig.SPEC.isLoaded() && LiquidGlassConfig.GLASS_BUTTONS.get();
    }

    /**
     * Draws the button background squashed as {@code scale(2 - s, s)} around its centre (spec §6, first-order area
     * compensation). The caller's {@code guiGraphics.setColor} alpha (widget fade) is applied by the glass shader.
     */
    public static void draw(GuiGraphics guiGraphics, int x, int y, int width, int height, float s) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + width * 0.5F, y + height * 0.5F, 0.0F);
        guiGraphics.pose().scale(2.0F - s, s, 1.0F);
        GlassRenderer.drawPanel(guiGraphics, -width * 0.5F, -height * 0.5F, width, height, GlassStyle.CLEAR);
        guiGraphics.pose().popPose();
    }
}
