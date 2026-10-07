package com.lczme.glazuli.mixin;

import com.lczme.glazuli.render.DarkText;
import com.lczme.glazuli.render.VanillaGlass;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The command completion popup as a glass popover with dark text; the scroll dots stay as they are. */
@Mixin(targets = "net.minecraft.client.gui.components.CommandSuggestions$SuggestionsList")
public abstract class CommandSuggestionsListMixin {
    @Unique
    private boolean glazuli$glass;

    @Inject(method = "render", at = @At("HEAD"))
    private void glazuli$beginDarkText(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        glazuli$glass = VanillaGlass.isEnabled();
        if (glazuli$glass) {
            DarkText.push();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void glazuli$endDarkText(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (glazuli$glass) {
            DarkText.pop();
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void glazuli$glassPopover(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (glazuli$glass && (color & 0xFFFFFF) == 0) {
            VanillaGlass.popover(guiGraphics, x1, y1, x2, y2);
        } else {
            original.call(guiGraphics, x1, y1, x2, y2, color);
        }
    }
}
