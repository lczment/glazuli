package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.render.DarkText;
import com.lczme.liquidglass.render.VanillaGlass;
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
    private boolean liquidglass$glass;

    @Inject(method = "renderUsage", at = @At("HEAD"))
    private void liquidglass$beginDarkText(GuiGraphics guiGraphics, CallbackInfo ci) {
        liquidglass$glass = VanillaGlass.isEnabled();
        if (liquidglass$glass) {
            DarkText.push();
        }
    }

    @Inject(method = "renderUsage", at = @At("RETURN"))
    private void liquidglass$endDarkText(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (liquidglass$glass) {
            DarkText.pop();
        }
    }

    @WrapOperation(method = "renderUsage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void liquidglass$glassRow(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (liquidglass$glass) {
            VanillaGlass.commandRow(guiGraphics, x1, y1, x2, y2);
        } else {
            original.call(guiGraphics, x1, y1, x2, y2, color);
        }
    }
}
