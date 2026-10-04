package com.lczme.liquidglass.mixin;

import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NeoForgeLoadingOverlay.class)
public interface NeoForgeLoadingOverlayAccessor {
    @Accessor("displayWindow")
    DisplayWindow liquidglass$getDisplayWindow();

    @Accessor("progressMeter")
    ProgressMeter liquidglass$getProgressMeter();
}
