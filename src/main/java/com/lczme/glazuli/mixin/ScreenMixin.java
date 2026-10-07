package com.lczme.glazuli.mixin;

import com.lczme.glazuli.config.GlazuliConfig;
import com.lczme.glazuli.render.GlassRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow
    protected Minecraft minecraft;

    @Shadow
    protected abstract void renderBlurredBackground(float partialTick);

    /**
     * Vanilla darkens whatever is behind a screen (dark menu texture, or a near-black gradient in-world). The light
     * glass would frost that into grey, so screens keep only the blur and every panel has the same brightness.
     */
    @Inject(method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", at = @At("HEAD"), cancellable = true)
    private void glazuli$undimmedMenuBackground(GuiGraphics guiGraphics, int x, int y, int width, int height, CallbackInfo ci) {
        if (glazuli$undimmed()) {
            ci.cancel();
        }
    }

    /** Inventories and containers only dim the world; without the dimming they blur it like the other menus. */
    @Inject(method = "renderTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void glazuli$undimmedBackdrop(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (glazuli$undimmed()) {
            ci.cancel();
            guiGraphics.flush();
            this.renderBlurredBackground(this.minecraft.getTimer().getGameTimeDeltaPartialTick(false));
        }
    }

    @Unique
    private boolean glazuli$undimmed() {
        return GlassRenderer.isGlassAvailable() && !GlazuliConfig.isClassic();
    }
}
