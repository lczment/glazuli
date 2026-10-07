package com.lczme.glazuli.compat.mixin.searchables;

import com.lczme.glazuli.render.DarkText;
import com.lczme.glazuli.render.VanillaGlass;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Searchables' completion list (Controlling's key bind search uses it): glass rows with dark text instead of black
 * fills. It fills each row on its own, black or, under the mouse, a lighter black.
 */
@Pseudo
@Mixin(targets = "com.blamejared.searchables.api.autcomplete.AutoComplete", remap = false)
public abstract class AutoCompleteMixin {
    @Unique
    private static final int SELECTED = 0xE0111111;

    @Unique
    private boolean glazuli$glass;

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void glazuli$beginDarkText(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        glazuli$glass = VanillaGlass.isEnabled();
        if (glazuli$glass) {
            DarkText.push();
        }
    }

    @Inject(method = "renderWidget", at = @At("RETURN"))
    private void glazuli$endDarkText(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (glazuli$glass) {
            glazuli$glass = false;
            DarkText.pop();
        }
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"))
    private void glazuli$glassRow(GuiGraphics guiGraphics, RenderType renderType, int x1, int y1, int x2, int y2, int color,
                                  Operation<Void> original) {
        if (!glazuli$glass) {
            original.call(guiGraphics, renderType, x1, y1, x2, y2, color);
            return;
        }
        VanillaGlass.commandRow(guiGraphics, x1, y1, x2, y2);
        if (color == SELECTED) {
            VanillaGlass.listSelection(guiGraphics, x1, y1, x2, y2, true);
        }
    }
}
