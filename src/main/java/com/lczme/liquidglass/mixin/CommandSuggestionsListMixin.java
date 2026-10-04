package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.render.DarkText;
import com.lczme.liquidglass.render.VanillaGlass;
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
    private boolean liquidglass$glass;

    @Inject(method = "render", at = @At("HEAD"))
    private void liquidglass$beginDarkText(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        liquidglass$glass = VanillaGlass.isEnabled();
        if (liquidglass$glass) {
            DarkText.push();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void liquidglass$endDarkText(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (liquidglass$glass) {
            DarkText.pop();
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void liquidglass$glassPopover(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (liquidglass$glass && (color & 0xFFFFFF) == 0) {
            VanillaGlass.popover(guiGraphics, x1, y1, x2, y2);
        } else {
            original.call(guiGraphics, x1, y1, x2, y2, color);
        }
    }
}
