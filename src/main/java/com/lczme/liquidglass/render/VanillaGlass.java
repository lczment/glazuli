package com.lczme.liquidglass.render;

import com.lczme.liquidglass.config.LiquidGlassConfig;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;

/**
 * Glass for the remaining vanilla UI: tooltips, toasts, list selection and scrollbars, text fields, checkboxes, tab
 * buttons, chat, boss bars and the advancements window. Light surfaces carry dark text ({@link DarkText}); controls
 * keep the clear button glass and vanilla's white text.
 */
public final class VanillaGlass {
    private static final GlassStyle POPOVER = ContainerGlass.SURFACE;
    /** Flat film rows without the top ring, so stacked lines read as one surface. */
    private static final GlassStyle ROW = new GlassStyle(POPOVER.material(), 0.0F, POPOVER.color(), POPOVER.tint(), POPOVER.shift(),
            POPOVER.falloff(), 0.0F, 0.0F, 1.0F, POPOVER.blur(), POPOVER.specular(), 0.0F);
    private static final Set<String> TOAST_BACKGROUNDS = Set.of("toast/advancement", "toast/recipe", "toast/system", "toast/tutorial");
    private static final GlassStyle CHECKED = GlassUi.SWITCH_ON.withCornerRadius(4.0F);
    private static final GlassStyle BOSS_TRACK = ContainerGlass.SURFACE.withCornerRadius(GlassStyle.CAPSULE).withShadow(0.0F);
    private static final int SEPARATOR = 0x40FFFFFF;
    private static final int SCROLLER = 0xB3FFFFFF;
    private static final int SCROLLER_TRACK = 0x26FFFFFF;
    private static final int CHECK = 0xFFFFFFFF;
    /** Vanilla's chat line background is black at 50 % opacity by default; that opacity maps to full glass. */
    private static final float CHAT_FULL_ALPHA = 127.5F;
    private static final ResourceLocation BOSS_BAR_SPRITE = ResourceLocation.withDefaultNamespace("boss_bar/white_background");
    private static final ResourceLocation ADVANCEMENTS_WINDOW = ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");

    private VanillaGlass() {
    }

    public static boolean isEnabled() {
        return LiquidGlassConfig.SPEC.isLoaded() && LiquidGlassConfig.GLASS_BUTTONS.get() && GlassRenderer.isGlassAvailable();
    }

    /** Replaces vanilla widget, toast and advancement-tab sprites; returns false to let vanilla draw. */
    public static boolean sprite(GuiGraphics guiGraphics, ResourceLocation sprite, int x, int y, int width, int height) {
        if (!sprite.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) || !isEnabled()) {
            return false;
        }
        String path = sprite.getPath();
        if (TOAST_BACKGROUNDS.contains(path)) {
            if (PackOverrides.sprite(sprite)) {
                return false;
            }
            GlassRenderer.recaptureBackdrop(guiGraphics);
            GlassRenderer.drawPanel(guiGraphics, x, y, width, height, POPOVER);
            return true;
        }
        if (path.startsWith("advancements/tab_") || path.startsWith("recipe_book/tab")) {
            if (PackOverrides.sprite(sprite)) {
                return false;
            }
            GlassRenderer.drawPanel(guiGraphics, x, y, width, height,
                    path.endsWith("_selected") ? ContainerGlass.TAB_SELECTED : ContainerGlass.TAB_UNSELECTED);
            return true;
        }
        if (!path.startsWith("widget/") || PackOverrides.sprite(sprite)) {
            return false;
        }
        switch (path) {
            case "widget/scroller" -> GlassUi.roundedFill(guiGraphics, x + 1, y, x + width - 1, y + height, 2, SCROLLER);
            case "widget/scroller_background" -> GlassUi.roundedFill(guiGraphics, x + 1, y, x + width - 1, y + height, 2, SCROLLER_TRACK);
            case "widget/checkbox", "widget/checkbox_highlighted" ->
                    GlassRenderer.drawPanel(guiGraphics, x, y, width, height, GlassButtons.BUTTON_CLEAR.withHover(path.endsWith("highlighted") ? 1.0F : 0.0F));
            case "widget/checkbox_selected", "widget/checkbox_selected_highlighted" -> {
                GlassRenderer.drawPanel(guiGraphics, x, y, width, height, CHECKED.withHover(path.endsWith("highlighted") ? 1.0F : 0.0F));
                drawCheck(guiGraphics, x, y, width, height);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** iOS-style check mark scaled to the box: a short stroke down to the right, then a long one up. */
    private static void drawCheck(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        float s = Math.min(width, height) / 17.0F;
        stroke(guiGraphics, x + 4.5F * s, y + 8.5F * s, x + 7.5F * s, y + 11.5F * s, s);
        stroke(guiGraphics, x + 7.5F * s, y + 11.5F * s, x + 12.5F * s, y + 5.0F * s, s);
    }

    private static void stroke(GuiGraphics guiGraphics, float x1, float y1, float x2, float y2, float s) {
        int steps = Math.max(1, Math.round(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1))));
        int t = Math.max(2, Math.round(2.0F * s));
        for (int i = 0; i <= steps; i++) {
            int px = Math.round(x1 + (x2 - x1) * i / steps);
            int py = Math.round(y1 + (y2 - y1) * i / steps);
            guiGraphics.fill(px - t / 2, py - t / 2, px - t / 2 + t, py - t / 2 + t, CHECK);
        }
    }

    /** Replaces dark list backgrounds and separators; returns false to let vanilla draw the texture. */
    public static boolean blit(GuiGraphics guiGraphics, ResourceLocation texture, int x1, int x2, int y1, int y2) {
        if (!texture.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) || !isEnabled()) {
            return false;
        }
        String path = texture.getPath();
        boolean separator = path.endsWith("header_separator.png") || path.endsWith("footer_separator.png");
        boolean hidden = path.endsWith("menu_list_background.png") || path.equals("textures/gui/tab_header_background.png")
                || path.equals("textures/gui/advancements/window.png");
        if (!separator && !hidden || PackOverrides.texture(texture)) {
            return false;
        }
        if (separator) {
            int y = path.contains("header") ? y2 - 1 : y1;
            guiGraphics.fill(x1, y, x2, y + 1, SEPARATOR);
        }
        return true;
    }

    public static void listSelection(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, boolean focused) {
        GlassRenderer.drawPanel(guiGraphics, x1, y1, x2 - x1, y2 - y1, GlassButtons.BUTTON_CLEAR.withHover(focused ? 1.0F : 0.0F));
    }

    /** {@code x, y, width, height}: the tooltip's text box, which vanilla's frame surrounds by 4 px. */
    public static void tooltip(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        tooltip(guiGraphics, x, y, width, height, false);
    }

    /** {@code depthTested}: for tooltips whose decorations were drawn first, in front at a higher z. */
    public static void tooltip(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean depthTested) {
        GlassRenderer.recaptureBackdrop(guiGraphics);
        GlassRenderer.drawPanel(guiGraphics, x - 4, y - 4, width + 8, height + 8, POPOVER, depthTested);
    }

    /** A chat line background (vanilla: black at the configured opacity), as seamless glass rows. */
    public static void chatLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        float opacity = Math.min(1.0F, (color >>> 24) / CHAT_FULL_ALPHA);
        if (opacity > 0.0F) {
            GlassRenderer.drawPanel(guiGraphics, x1, y1, x2 - x1, y2 - y1, ROW.withOpacity(opacity));
        }
    }

    /** A line of command usage hints, stacked like chat rows. */
    public static void commandRow(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2) {
        GlassRenderer.drawPanel(guiGraphics, x1, y1, x2 - x1, y2 - y1, ROW);
    }

    public static void popover(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2) {
        GlassRenderer.recaptureBackdrop(guiGraphics);
        GlassRenderer.drawPanel(guiGraphics, x1, y1, x2 - x1, y2 - y1, POPOVER.withCornerRadius(4.0F));
    }

    public static void button(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean hovered) {
        GlassRenderer.drawPanel(guiGraphics, x, y, width, height, GlassButtons.BUTTON_CLEAR.withHover(hovered ? 1.0F : 0.0F));
    }

    public static void chatInput(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2) {
        GlassRenderer.drawPanel(guiGraphics, x1, y1, x2 - x1, y2 - y1, POPOVER.withCornerRadius(4.0F));
    }

    public static boolean bossBarOverridden() {
        return PackOverrides.sprite(BOSS_BAR_SPRITE);
    }

    public static void advancementsWindow(GuiGraphics guiGraphics, int x, int y) {
        if (isEnabled() && !PackOverrides.texture(ADVANCEMENTS_WINDOW)) {
            GlassRenderer.drawPanel(guiGraphics, x, y, 252, 140, ContainerGlass.WINDOW);
        }
    }

    public static void bossBar(GuiGraphics guiGraphics, int x, int y, BossEvent event) {
        int width = 182;
        int height = 5;
        GlassRenderer.drawPanel(guiGraphics, x, y, width, height, BOSS_TRACK);
        int filled = Math.round(event.getProgress() * width);
        if (filled > 0) {
            GlassRenderer.fillRounded(guiGraphics, x, y, Math.max(filled, height), height, height * 0.5F, bossColor(event.getColor()));
        }
    }

    private static int bossColor(BossEvent.BossBarColor color) {
        return switch (color) {
            case PINK -> 0xFFF472B6;
            case BLUE -> 0xFF60A5FA;
            case RED -> 0xFFF87171;
            case GREEN -> 0xFF4ADE80;
            case YELLOW -> 0xFFFACC15;
            case PURPLE -> 0xFFC084FC;
            case WHITE -> 0xFFF5F5F5;
        };
    }
}
