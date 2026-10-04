package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.render.GlassButtons;
import com.lczme.liquidglass.render.GlassRenderer;
import com.lczme.liquidglass.render.GlassStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Catches the vanilla button/slider sprites wherever they are drawn (sliders, modded widgets), not just in AbstractButton. */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Inject(method = "blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V", at = @At("HEAD"), cancellable = true)
    private void liquidglass$glassWidgetSprite(ResourceLocation sprite, int x, int y, int width, int height, CallbackInfo ci) {
        if (!GlassButtons.isEnabled()) {
            return;
        }
        GlassStyle style = GlassButtons.styleForSprite(sprite);
        if (style != null) {
            GlassRenderer.drawPanel((GuiGraphics) (Object) this, x, y, width, height, style);
            ci.cancel();
        }
    }
}
