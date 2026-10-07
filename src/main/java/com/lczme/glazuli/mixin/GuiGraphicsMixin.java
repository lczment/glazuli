package com.lczme.glazuli.mixin;

import com.lczme.glazuli.render.ContainerGlass;
import com.lczme.glazuli.render.DarkText;
import com.lczme.glazuli.render.GlassButtons;
import com.lczme.glazuli.render.GlassRenderer;
import com.lczme.glazuli.render.GlassStyle;
import com.lczme.glazuli.render.VanillaGlass;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
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
    private void glazuli$glassWidgetSprite(ResourceLocation sprite, int x, int y, int width, int height, CallbackInfo ci) {
        if (ContainerGlass.sprite((GuiGraphics) (Object) this, sprite, x, y, width, height)
                || VanillaGlass.sprite((GuiGraphics) (Object) this, sprite, x, y, width, height)) {
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
    private void glazuli$glassContainerTexture(ResourceLocation texture, int x1, int x2, int y1, int y2, int z,
                                                   float u0, float u1, float v0, float v1, CallbackInfo ci) {
        if (ContainerGlass.blit((GuiGraphics) (Object) this, texture, x1, x2, y1, y2, z, u0, u1, v0, v1)
                || VanillaGlass.blit((GuiGraphics) (Object) this, texture, x1, x2, y1, y2)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"))
    private void glazuli$beginTooltipText(Font font, List<ClientTooltipComponent> components, int mouseX, int mouseY,
                                              ClientTooltipPositioner positioner, CallbackInfo ci) {
        if (VanillaGlass.isEnabled()) {
            DarkText.push();
        }
    }

    @Inject(method = "renderTooltipInternal", at = @At("RETURN"))
    private void glazuli$endTooltipText(Font font, List<ClientTooltipComponent> components, int mouseX, int mouseY,
                                            ClientTooltipPositioner positioner, CallbackInfo ci) {
        if (VanillaGlass.isEnabled()) {
            DarkText.pop();
        }
    }
}
