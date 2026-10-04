package com.lczme.liquidglass.compat.sodium.gui.theme;

import com.lczme.liquidglass.render.GlassUi;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.minecraft.util.FastColor;

public final class GuiThemes {
    public static final GuiTheme DEFAULT_BUTTON = new GuiTheme(GlassUi.TEXT, GlassUi.TEXT, 0xFF8E8E93, 0xE0000000, 0x90000000, 0x40000000);
    public static final int FLAT_BUTTON_FOCUS_BORDER = 0x8000FFEE;
    public static final int OPTION_FOCUS_BORDER = GlassUi.TEXT;
    public static final int SELECTED_UNDERLINE = 0xFF94E4D3;

    public static GuiTheme fromSodium(ColorTheme theme) {
        // Sodium's palettes are pastels made for dark backgrounds; darkened (same hue) they read on the light cards.
        return new GuiTheme(
                darken(theme.theme, 0.7F),
                darken(theme.themeLighter, 0.35F),
                darken(theme.themeDarker, 0.5F),
                DEFAULT_BUTTON.bgHighlight,
                DEFAULT_BUTTON.bgDefault,
                DEFAULT_BUTTON.bgInactive
        );
    }

    private static int darken(int argb, float factor) {
        return FastColor.ARGB32.color(FastColor.ARGB32.alpha(argb), Math.round(FastColor.ARGB32.red(argb) * factor),
                Math.round(FastColor.ARGB32.green(argb) * factor), Math.round(FastColor.ARGB32.blue(argb) * factor));
    }
}
