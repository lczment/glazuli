package com.lczme.glazuli.compat;

import com.lczme.glazuli.compat.sodium.SodiumScreens;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/** Entry point for optional integrations; classes that reference another mod are only touched when it is loaded. */
public final class Compat {
    private Compat() {
    }

    public static void register(IEventBus gameBus) {
        if (ModList.get().isLoaded("sodium")) {
            gameBus.addListener(SodiumScreens::onScreenOpening);
        }
    }
}
