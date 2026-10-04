package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.cave.CaveBackground;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the cave sharp behind menus: the glass controls refract it, and the camera moves are the point of it. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void liquidglass$keepCaveSharp(float partialTick, CallbackInfo ci) {
        if (CaveBackground.isActive()) {
            ci.cancel();
        }
    }
}
