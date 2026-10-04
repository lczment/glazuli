package com.lczme.liquidglass.render;

import com.lczme.liquidglass.config.LiquidGlassConfig;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Glass look for vanilla buttons, applied by {@code AbstractButtonMixin} in place of the button sprite.
 * Buttons use transparent glass ({@link GlassStyle#CLEAR}), except title screen buttons which use the neutral level
 * because the panorama has too little detail for clear glass to be visible. A button's look never changes with its
 * state: feedback is the press spring and vanilla's label colour (grey when inactive). Like a Web button, a mouse
 * click acts on release over the button, so the squash is actually visible before the screen changes.
 */
public final class GlassButtons {
    /** Spec §6 button press: target scale and spring (response 0.28 s, damping ratio 0.68). */
    public static final float PRESSED_SCALE = 0.92F;
    public static final float PRESS_RESPONSE = 0.28F;
    public static final float PRESS_DAMPING = 0.68F;

    private static final Set<AbstractWidget> NEUTRAL = Collections.newSetFromMap(new WeakHashMap<>());

    private GlassButtons() {
    }

    /** Draws {@code widget} with the neutral glass level instead of clear glass (title screen, low-detail backdrop). */
    public static void useNeutral(AbstractWidget widget) {
        NEUTRAL.add(widget);
    }

    public static GlassStyle styleFor(AbstractWidget widget) {
        return NEUTRAL.contains(widget) ? GlassStyle.NEUTRAL : GlassStyle.CLEAR;
    }

    public static boolean isEnabled() {
        return LiquidGlassConfig.SPEC.isLoaded() && LiquidGlassConfig.GLASS_BUTTONS.get();
    }

    /**
     * Pushes the press squash {@code scale(2 - s, s)} around the widget's centre (spec §6, first-order area
     * compensation). Applied to the whole widget so the label and icons move with the glass; pair with popPose.
     */
    public static void pushSquash(GuiGraphics guiGraphics, AbstractWidget widget, float s) {
        float cx = widget.getX() + widget.getWidth() * 0.5F;
        float cy = widget.getY() + widget.getHeight() * 0.5F;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(cx, cy, 0.0F);
        guiGraphics.pose().scale(2.0F - s, s, 1.0F);
        guiGraphics.pose().translate(-cx, -cy, 0.0F);
    }
}
