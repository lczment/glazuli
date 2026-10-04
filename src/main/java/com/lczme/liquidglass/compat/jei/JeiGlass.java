package com.lczme.liquidglass.compat.jei;

import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.lczme.liquidglass.render.ContainerGlass;
import com.lczme.liquidglass.render.GlassButtons;
import com.lczme.liquidglass.render.GlassRenderer;
import com.lczme.liquidglass.render.GlassStyle;
import com.lczme.liquidglass.render.GlassUi;
import com.lczme.liquidglass.render.PackOverrides;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Glass replacements for JEI's GUI sprites, keyed by sprite name. Icons, arrows and the exclusion shadow keep JEI's own
 * art.
 */
public final class JeiGlass {
    private static final GlassStyle WINDOW = ContainerGlass.WINDOW;
    private static final GlassStyle SIDE_TAB = ContainerGlass.TAB_SELECTED;
    private static final GlassStyle TAB_UNSELECTED = ContainerGlass.TAB_UNSELECTED;
    private static final GlassStyle PRESSED = GlassButtons.BUTTON_NEUTRAL;
    /** Recessed well, so items read against the glass the way they do in a vanilla slot. */
    private static final int SLOT = 0x38000000;

    private static final Map<ResourceLocation, Look> LOOKS = Map.ofEntries(
            entry("button_enabled_v2", Look.glass(GlassButtons.BUTTON_CLEAR)),
            entry("button_highlight_v2", Look.glass(GlassButtons.BUTTON_CLEAR.withHover(1.0F))),
            entry("button_disabled_v2", Look.glass(GlassButtons.BUTTON_CLEAR.withOpacity(GlassUi.DISABLED_OPACITY))),
            entry("button_pressed_v2", Look.glass(PRESSED)),
            entry("button_pressed_highlight_v2", Look.glass(PRESSED.withHover(1.0F))),
            entry("gui_background_v2", Look.glass(WINDOW)),
            entry("ingredient_list_background_v2", Look.glass(WINDOW)),
            entry("bookmark_list_background_v2", Look.glass(WINDOW)),
            entry("tab_selected", Look.glass(SIDE_TAB)),
            entry("tab_unselected", Look.glass(TAB_UNSELECTED)),
            entry("slot", Look.fill(SLOT, 2)),
            entry("output_slot", Look.fill(SLOT, 3)),
            entry("recipe_catalyst_slot_background_v2", Look.fill(SLOT, 2)),
            entry("catalyst_tab_v2", Look.glass(SIDE_TAB)),
            entry("recipe_options_tab_v2", Look.glass(SIDE_TAB)),
            entry("recipe_preview_background_v2", Look.popover(ContainerGlass.SURFACE)),
            entry("interactive_ingredient_tooltip_background", Look.popover(ContainerGlass.SURFACE)),
            entry("search_background_v2", Look.glass(GlassUi.FIELD)),
            entry("single_recipe_background_v2", Look.fill(GlassUi.ROW, 3)),
            entry("scrollbar_background_v2", Look.fill(GlassUi.ROW, 2)),
            entry("scrollbar_marker_v2", Look.fill(GlassUi.SCROLL_THUMB_ACTIVE, 2)));

    private JeiGlass() {
    }

    /** Draws {@code sprite} as glass and returns true, or returns false to let JEI draw it. */
    public static boolean draw(GuiGraphics guiGraphics, ResourceLocation sprite, int x, int y, int width, int height) {
        if (!LiquidGlassConfig.SPEC.isLoaded() || !LiquidGlassConfig.GLASS_JEI.get()) {
            return false;
        }
        Look look = LOOKS.get(sprite);
        if (look == null || PackOverrides.sprite(sprite)) {
            return false;
        }
        if (look.style() == null) {
            GlassUi.roundedFill(guiGraphics, x, y, x + width, y + height, look.radius(), look.color());
            return true;
        }
        if (look.popover()) {
            GlassRenderer.recaptureBackdrop(guiGraphics);
        }
        GlassRenderer.drawPanel(guiGraphics, x, y, width, height, look.style());
        return true;
    }

    private static Map.Entry<ResourceLocation, Look> entry(String sprite, Look look) {
        return Map.entry(ResourceLocation.fromNamespaceAndPath("jei", sprite), look);
    }

    /** Either a glass panel ({@code style}, optionally over a fresh backdrop) or a rounded translucent fill. */
    private record Look(GlassStyle style, boolean popover, int color, int radius) {
        static Look glass(GlassStyle style) {
            return new Look(style, false, 0, 0);
        }

        static Look popover(GlassStyle style) {
            return new Look(style, true, 0, 0);
        }

        static Look fill(int color, int radius) {
            return new Look(null, false, color, radius);
        }
    }
}
