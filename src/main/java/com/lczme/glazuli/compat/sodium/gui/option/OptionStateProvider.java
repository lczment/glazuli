package com.lczme.glazuli.compat.sodium.gui.option;

import net.caffeinemc.mods.sodium.client.config.structure.Config;
import org.jetbrains.annotations.Nullable;

public interface OptionStateProvider {
    @Nullable Config glazuli$getParentConfig();
}
