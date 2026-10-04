package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.title.TitleMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Shadow
    private boolean fading;
    @Shadow
    private long fadeInStart;

    @Unique
    private TitleMenu liquidglass$menu;

    @Inject(method = "init", at = @At("TAIL"))
    private void liquidglass$layout(CallbackInfo ci) {
        if (!TitleMenu.isEnabled()) {
            return;
        }
        if (liquidglass$menu == null) {
            liquidglass$menu = new TitleMenu();
        }
        liquidglass$menu.layout((Screen) (Object) this);
    }

    /**
     * Drives vanilla's title fade with our entrance: while fading, vanilla's opacity is
     * {@code clampedMap((now - fadeInStart) / 2000, 0.5, 1, 0, 1)}, so choosing {@code fadeInStart} sets it exactly.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void liquidglass$beforeRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (liquidglass$menu == null) {
            return;
        }
        liquidglass$menu.beforeRender();
        float alpha = liquidglass$menu.chromeAlpha();
        fading = alpha < 1.0F;
        if (fading) {
            fadeInStart = Util.getMillis() - (long) (1000.0F + 1000.0F * alpha);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;fadeWidgets(F)V"))
    private void liquidglass$afterFade(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (liquidglass$menu != null) {
            liquidglass$menu.afterVanillaFade();
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
    private void liquidglass$logoEntrance(LogoRenderer logo, GuiGraphics guiGraphics, int width, float alpha, Operation<Void> original) {
        if (liquidglass$menu == null || !liquidglass$menu.drawLogo(guiGraphics, width)) {
            original.call(logo, guiGraphics, width, alpha);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;renderPanorama(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
    private void liquidglass$drawBackdrop(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (liquidglass$menu != null) {
            Screen self = (Screen) (Object) this;
            liquidglass$menu.drawBackdrop(guiGraphics, self.width, self.height);
        }
    }
}
