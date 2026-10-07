package com.lczme.glazuli.compat.mixin.jade;

import com.lczme.glazuli.render.ContainerGlass;
import com.lczme.glazuli.render.DarkText;
import com.lczme.glazuli.render.GlassRenderer;
import com.lczme.glazuli.render.VanillaGlass;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.impl.ui.BoxElement;
import snownee.jade.impl.ui.StyledElement;

/**
 * Jade's HUD tooltip on light glass with dark text. Only the outermost box (the theme's tooltip style) is replaced;
 * nested boxes and progress bars keep the theme look.
 */
@Mixin(value = BoxElement.class, remap = false)
public abstract class JadeBoxElementMixin {
    @Unique
    private static int glazuli$depth;

    @Inject(method = "render", at = @At("HEAD"))
    private void glazuli$enter(GuiGraphics guiGraphics, float x, float y, float maxX, float maxY, CallbackInfo ci) {
        if (glazuli$depth++ == 0 && VanillaGlass.isEnabled()) {
            DarkText.push();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void glazuli$exit(GuiGraphics guiGraphics, float x, float y, float maxX, float maxY, CallbackInfo ci) {
        if (--glazuli$depth == 0 && VanillaGlass.isEnabled()) {
            DarkText.pop();
        }
        glazuli$depth = Math.max(0, glazuli$depth);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lsnownee/jade/api/ui/BoxStyle;render(Lnet/minecraft/client/gui/GuiGraphics;Lsnownee/jade/impl/ui/StyledElement;FFFFF)V"))
    private void glazuli$glassBox(BoxStyle style, GuiGraphics guiGraphics, StyledElement element, float x, float y,
                                      float width, float height, float alpha, Operation<Void> original) {
        if (glazuli$depth == 1 && VanillaGlass.isEnabled()) {
            GlassRenderer.drawPanel(guiGraphics, x, y, width, height, ContainerGlass.SURFACE.withOpacity(alpha));
        } else {
            original.call(style, guiGraphics, element, x, y, width, height, alpha);
        }
    }
}
