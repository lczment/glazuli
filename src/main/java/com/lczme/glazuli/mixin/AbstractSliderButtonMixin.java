package com.lczme.glazuli.mixin;

import com.lczme.glazuli.anim.Spring;
import com.lczme.glazuli.render.GlassButtons;
import com.lczme.glazuli.render.GlassRenderer;
import com.lczme.glazuli.render.GlassStyle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Sliders get the same hover glow as buttons (the generic sprite hook in GuiGraphicsMixin doesn't know the widget). */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
    @Unique
    private Spring glazuli$hover;

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void glazuli$hoverGlass(GuiGraphics guiGraphics, ResourceLocation sprite, int x, int y, int width, int height,
                                        Operation<Void> original) {
        GlassStyle style = GlassButtons.isEnabled() ? GlassButtons.styleForSprite(sprite) : null;
        if (style == null) {
            original.call(guiGraphics, sprite, x, y, width, height);
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        if (glazuli$hover == null) {
            glazuli$hover = Spring.of(GlassButtons.HOVER_RESPONSE, GlassButtons.HOVER_DAMPING, 0.0F);
        }
        glazuli$hover.setTarget(self.active && self.isHoveredOrFocused());
        GlassRenderer.drawPanel(guiGraphics, x, y, width, height, style.withHover(Math.max(0.0F, glazuli$hover.value())));
    }
}
