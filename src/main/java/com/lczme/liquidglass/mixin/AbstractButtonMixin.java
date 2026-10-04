package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.anim.Spring;
import com.lczme.liquidglass.render.GlassButtons;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Swaps only the background sprite, so labels and subclass decorations (e.g. icon buttons) still render as usual. */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
    @Unique
    private Spring liquidglass$hover;

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void liquidglass$drawGlassBackground(GuiGraphics guiGraphics, ResourceLocation sprite, int x, int y, int width, int height,
                                                 Operation<Void> original) {
        if (!GlassButtons.isEnabled()) {
            original.call(guiGraphics, sprite, x, y, width, height);
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        if (liquidglass$hover == null) {
            liquidglass$hover = Spring.bouncy(0.0F);
        }
        liquidglass$hover.setTarget(self.active && self.isHoveredOrFocused());
        GlassButtons.draw(guiGraphics, x, y, width, height, self.active, liquidglass$hover.value());
    }
}
