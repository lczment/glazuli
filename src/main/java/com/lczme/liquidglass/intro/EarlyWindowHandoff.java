package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.LiquidGlass;
import net.minecraft.util.FastColor;
import net.neoforged.fml.earlydisplay.ColourScheme;
import net.neoforged.fml.earlydisplay.DisplayWindow;

/** The FML early loading window we take over from: its background colour, and closing it once the intro is done. */
final class EarlyWindowHandoff {
    private final DisplayWindow window;
    private final int background;
    private boolean closed;

    EarlyWindowHandoff(DisplayWindow window) {
        this.window = window;
        ColourScheme.Colour c = window.context().colourScheme().background();
        this.background = FastColor.ARGB32.color(255, c.red(), c.green(), c.blue());
    }

    int background() {
        return background;
    }

    /** Frees the early window's GL resources, as {@code NeoForgeLoadingOverlay} does when it finishes. */
    void close() {
        if (!closed) {
            closed = true;
            try {
                window.close();
            } catch (Throwable t) {
                LiquidGlass.LOGGER.warn("Failed to close the early loading window", t);
            }
        }
    }
}
