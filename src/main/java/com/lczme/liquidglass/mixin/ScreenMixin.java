package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.cave.CaveBackground;
import com.lczme.liquidglass.render.GlassRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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

    /**
     * Vanilla darkens whatever is behind a screen (dark menu texture, or a near-black gradient in-world). The light
     * glass would frost that into grey, so screens keep only the blur and every panel has the same brightness.
     */
    @Inject(method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", at = @At("HEAD"), cancellable = true)
    private void liquidglass$undimmedMenuBackground(GuiGraphics guiGraphics, int x, int y, int width, int height, CallbackInfo ci) {
        if (GlassRenderer.isGlassAvailable()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void liquidglass$undimmedBackdrop(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (GlassRenderer.isGlassAvailable()) {
            ci.cancel();
        }
    }
}
