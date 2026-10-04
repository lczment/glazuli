package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.render.ContainerGlass;
import com.lczme.liquidglass.render.GlassButtons;
import com.lczme.liquidglass.render.GlassRenderer;
import com.lczme.liquidglass.render.GlassStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches the vanilla button/slider sprites wherever they are drawn (sliders, modded widgets), not just in
 * AbstractButton, plus container textures, HUD/creative-tab sprites for {@link ContainerGlass}.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Inject(method = "blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V", at = @At("HEAD"), cancellable = true)
    private void liquidglass$glassWidgetSprite(ResourceLocation sprite, int x, int y, int width, int height, CallbackInfo ci) {
        if (ContainerGlass.sprite((GuiGraphics) (Object) this, sprite, x, y, width, height)) {
            ci.cancel();
            return;
        }
        if (!GlassButtons.isEnabled()) {
            return;
        }
        GlassStyle style = GlassButtons.styleForSprite(sprite);
        if (style != null) {
            GlassRenderer.drawPanel((GuiGraphics) (Object) this, x, y, width, height, style);
            ci.cancel();
        }
    }

    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V", at = @At("HEAD"), cancellable = true)
    private void liquidglass$glassContainerTexture(ResourceLocation texture, int x1, int x2, int y1, int y2, int z,
                                                   float u0, float u1, float v0, float v1, CallbackInfo ci) {
        if (ContainerGlass.blit((GuiGraphics) (Object) this, texture, x1, x2, y1, y2, z, u0, u1, v0, v1)) {
            ci.cancel();
        }
    }
}
