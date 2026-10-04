package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.render.VanillaGlass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A glass window behind the advancement tree; the window frame texture itself is skipped in {@link VanillaGlass#blit}. */
@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsScreenMixin {
    @Inject(method = "renderInside", at = @At("HEAD"))
    private void liquidglass$glassWindow(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, CallbackInfo ci) {
        VanillaGlass.advancementsWindow(guiGraphics, x, y);
    }
}
