package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.cave.CaveBackground;
import com.lczme.liquidglass.render.ContainerGlass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    /** Keeps the cave sharp behind menus: the glass controls refract it, and the camera moves are the point of it. */
    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void liquidglass$keepCaveSharp(float partialTick, CallbackInfo ci) {
        if (CaveBackground.isActive()) {
            ci.cancel();
        }
    }

    /** Container screens dim the world almost black, which the light window glass would blur into dark grey. */
    @Inject(method = "renderTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void liquidglass$undimmedContainerBackdrop(GuiGraphics guiGraphics, CallbackInfo ci) {
        if ((Object) this instanceof AbstractContainerScreen<?> && ContainerGlass.isEnabled()) {
            ci.cancel();
        }
    }
}
