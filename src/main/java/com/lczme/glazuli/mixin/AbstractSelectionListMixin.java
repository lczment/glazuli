package com.lczme.glazuli.mixin;

import com.lczme.glazuli.render.VanillaGlass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The selected row as a glass plate instead of vanilla's white/grey outline. */
@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {
    @Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true)
    private void glazuli$glassSelection(GuiGraphics guiGraphics, int top, int width, int height, int outerColor, int innerColor, CallbackInfo ci) {
        if (!VanillaGlass.isEnabled()) {
            return;
        }
        AbstractWidget list = (AbstractWidget) (Object) this;
        int x1 = list.getX() + (list.getWidth() - width) / 2;
        VanillaGlass.listSelection(guiGraphics, x1, top - 2, x1 + width, top + height + 2, outerColor == -1);
        ci.cancel();
    }
}
