package com.lczme.glazuli.mixin;

import com.lczme.glazuli.render.DarkText;
import com.lczme.glazuli.render.VanillaGlass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Toast backgrounds become light glass in {@link VanillaGlass#sprite}; their text goes dark here. */
@Mixin(ToastComponent.class)
public abstract class ToastComponentMixin {
    @Unique
    private boolean glazuli$darkText;

    @Inject(method = "render", at = @At("HEAD"))
    private void glazuli$beginDarkText(GuiGraphics guiGraphics, CallbackInfo ci) {
        glazuli$darkText = VanillaGlass.isEnabled();
        if (glazuli$darkText) {
            DarkText.push();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void glazuli$endDarkText(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (glazuli$darkText) {
            DarkText.pop();
        }
    }
}
