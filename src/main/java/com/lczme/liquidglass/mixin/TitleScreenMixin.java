package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.title.TitleMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
    private float panoramaFade;

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

    @Inject(method = "render", at = @At("HEAD"))
    private void liquidglass$beforeRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (liquidglass$menu != null) {
            // Vanilla's own 2 s fade-in would play on top of the intro outro / our entrance animation.
            fading = false;
            panoramaFade = 1.0F;
            liquidglass$menu.beforeRender();
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

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
    private void liquidglass$drawLogo(LogoRenderer renderer, GuiGraphics guiGraphics, int width, float alpha, Operation<Void> original) {
        if (liquidglass$menu == null) {
            original.call(renderer, guiGraphics, width, alpha);
            return;
        }
        liquidglass$menu.drawLogo(guiGraphics, width, ((Screen) (Object) this).height);
    }
}
