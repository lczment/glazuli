package com.lczme.liquidglass.render;

import com.lczme.liquidglass.config.LiquidGlassConfig;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.ResourceLocation;

/**
 * Glass look for vanilla buttons, applied by {@code AbstractButtonMixin} in place of the button sprite, and by
 * {@code GuiGraphicsMixin} wherever else the vanilla button/slider sprites are drawn. The sprites are never read, so
 * resource packs that retexture widgets don't change the look.
 * <p>
 * Buttons use transparent glass ({@link #BUTTON_CLEAR}), except title screen buttons which use the neutral level
 * because the panorama has too little detail for clear glass to be visible. A button's look never changes with its
 * state: feedback is the press spring and vanilla's label colour (grey when inactive). Like a Web button, a mouse
 * click acts on release over the button, so the squash is actually visible before the screen changes.
 */
public final class GlassButtons {
    /** Spec §6 button press: target scale and spring (response 0.28 s, damping ratio 0.68). */
    public static final float PRESSED_SCALE = 0.92F;
    public static final float PRESS_RESPONSE = 0.28F;
    public static final float PRESS_DAMPING = 0.68F;
    /**
     * Release spring, re-tuned per §13: the spec's 0.68 overshoots by under 0.5 %, which reads as no rebound at
     * Minecraft's button sizes. 0.34 overshoots ~3 % and settles in about two wobbles.
     */
    public static final float RELEASE_RESPONSE = 0.34F;
    public static final float RELEASE_DAMPING = 0.34F;
    /** The click acts this long after release, so the rebound is seen before the screen fades out. */
    public static final float ACTION_DELAY = 0.14F;

    /** Rounded rectangle rather than a capsule: Minecraft's buttons are wide and short, pills read as text fields. */
    public static final float BUTTON_RADIUS = 4.0F;
    public static final GlassStyle BUTTON_CLEAR = GlassStyle.button(BUTTON_RADIUS, 0.08F);
    /** A little more body where the backdrop is busy and labels need contrast (title screen). */
    public static final GlassStyle BUTTON_NEUTRAL = GlassStyle.button(BUTTON_RADIUS, 0.18F);

    /** Hover: the glass brightens and lifts slightly, like iOS 26 pointer hover. */
    public static final float HOVER_RESPONSE = 0.3F;
    public static final float HOVER_DAMPING = 0.8F;
    public static final float HOVER_GROW = 0.025F;

    private static final Map<ResourceLocation, GlassStyle> SPRITES = Map.of(
            ResourceLocation.withDefaultNamespace("widget/button"), BUTTON_CLEAR,
            ResourceLocation.withDefaultNamespace("widget/button_highlighted"), BUTTON_CLEAR,
            ResourceLocation.withDefaultNamespace("widget/button_disabled"), BUTTON_CLEAR,
            ResourceLocation.withDefaultNamespace("widget/slider"), BUTTON_CLEAR,
            ResourceLocation.withDefaultNamespace("widget/slider_highlighted"), BUTTON_CLEAR,
            ResourceLocation.withDefaultNamespace("widget/slider_handle"), BUTTON_NEUTRAL,
            ResourceLocation.withDefaultNamespace("widget/slider_handle_highlighted"), BUTTON_NEUTRAL);

    private static final Set<AbstractWidget> NEUTRAL = Collections.newSetFromMap(new WeakHashMap<>());

    private GlassButtons() {
    }

    /** Draws {@code widget} with the neutral glass level instead of clear glass (title screen, low-detail backdrop). */
    public static void useNeutral(AbstractWidget widget) {
        NEUTRAL.add(widget);
    }

    public static GlassStyle styleFor(AbstractWidget widget) {
        return NEUTRAL.contains(widget) ? BUTTON_NEUTRAL : BUTTON_CLEAR;
    }

    /** Glass replacing a vanilla widget sprite, or {@code null} for sprites we leave alone. */
    @Nullable
    public static GlassStyle styleForSprite(ResourceLocation sprite) {
        return SPRITES.get(sprite);
    }

    public static boolean isEnabled() {
        return LiquidGlassConfig.SPEC.isLoaded() && LiquidGlassConfig.GLASS_BUTTONS.get();
    }

    /**
     * Pushes the hover lift and the press squash {@code scale(2 - s, s)} (spec §6, first-order area compensation)
     * around the widget's centre. Applied to the whole widget so the label and icons move with the glass; pair with
     * popPose.
     */
    public static void pushTransform(GuiGraphics guiGraphics, AbstractWidget widget, float s, float hover) {
        float cx = widget.getX() + widget.getWidth() * 0.5F;
        float cy = widget.getY() + widget.getHeight() * 0.5F;
        float grow = 1.0F + HOVER_GROW * hover;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(cx, cy, 0.0F);
        guiGraphics.pose().scale((2.0F - s) * grow, s * grow, 1.0F);
        guiGraphics.pose().translate(-cx, -cy, 0.0F);
    }
}
