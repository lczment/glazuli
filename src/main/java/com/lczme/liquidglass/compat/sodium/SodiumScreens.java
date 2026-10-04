package com.lczme.liquidglass.compat.sodium;

import com.lczme.liquidglass.compat.sodium.gui.SodiumVideoOptionsScreen;
import com.lczme.liquidglass.config.LiquidGlassConfig;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Swaps Sodium's video settings (and Reese's Sodium Options' replacement, if installed) for the glass port before
 * either is initialised, so their own screen never flashes up.
 */
public final class SodiumScreens {
    private static final String RSO_SCREEN = "me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen";

    private SodiumScreens() {
    }

    /** Sodium's own entry point (what its options button calls); opening the result lands on the glass port. */
    public static Screen videoSettings(@Nullable Screen parent) {
        return VideoSettingsScreen.createScreen(parent);
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (!LiquidGlassConfig.GLASS_SODIUM_SCREEN.get()
                || !(next instanceof VideoSettingsScreen || next.getClass().getName().equals(RSO_SCREEN))) {
            return;
        }
        event.setNewScreen(new SodiumVideoOptionsScreen(event.getCurrentScreen()));
    }
}
