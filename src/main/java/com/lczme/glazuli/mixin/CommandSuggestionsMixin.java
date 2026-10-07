package com.lczme.glazuli.mixin;

import com.lczme.glazuli.render.DarkText;
import com.lczme.glazuli.render.VanillaGlass;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Command usage hints under the chat input as light glass rows with dark text. */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Unique
    private boolean glazuli$glass;

    @Inject(method = "renderUsage", at = @At("HEAD"))
    private void glazuli$beginDarkText(GuiGraphics guiGraphics, CallbackInfo ci) {
        glazuli$glass = VanillaGlass.isEnabled();
        if (glazuli$glass) {
            DarkText.push();
        }
    }

    @Inject(method = "renderUsage", at = @At("RETURN"))
    private void glazuli$endDarkText(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (glazuli$glass) {
            DarkText.pop();
        }
    }

    @WrapOperation(method = "renderUsage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void glazuli$glassRow(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (glazuli$glass) {
            VanillaGlass.commandRow(guiGraphics, x1, y1, x2, y2);
        } else {
            original.call(guiGraphics, x1, y1, x2, y2, color);
        }
    }
}
