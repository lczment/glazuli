package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.render.GlassButtons;
import com.lczme.liquidglass.render.GlassPressable;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lifts and squashes the whole button (glass, label, subclass icons); wraps renderWidget so overrides are covered. */
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/AbstractWidget;renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void liquidglass$pressSquash(AbstractWidget self, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                         Operation<Void> original) {
        if (!((Object) self instanceof GlassPressable pressable) || !GlassButtons.isEnabled()) {
            original.call(self, guiGraphics, mouseX, mouseY, partialTick);
            return;
        }
        float s = pressable.liquidglass$pressScale();
        float hover = pressable.liquidglass$hover();
        if (s == 1.0F && hover == 0.0F) {
            original.call(self, guiGraphics, mouseX, mouseY, partialTick);
            return;
        }
        GlassButtons.pushTransform(guiGraphics, self, s, hover);
        original.call(self, guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.pose().popPose();
    }
}
