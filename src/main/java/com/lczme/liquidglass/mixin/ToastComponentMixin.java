package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.render.DarkText;
import com.lczme.liquidglass.render.VanillaGlass;
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
    private boolean liquidglass$darkText;

    @Inject(method = "render", at = @At("HEAD"))
    private void liquidglass$beginDarkText(GuiGraphics guiGraphics, CallbackInfo ci) {
        liquidglass$darkText = VanillaGlass.isEnabled();
        if (liquidglass$darkText) {
            DarkText.push();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void liquidglass$endDarkText(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (liquidglass$darkText) {
            DarkText.pop();
        }
    }
}
