package com.lczme.glazuli.compat.mixin.sophisticatedcore;

import com.lczme.glazuli.render.ContainerGlass;
import com.lczme.glazuli.render.GlassRenderer;
import com.lczme.glazuli.render.PackOverrides;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sophisticated Storage and Backpacks: the side tabs (upgrades, settings) on glass instead of their tiled grey frame. */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper", remap = false)
public abstract class GuiHelperMixin {
    @Unique
    private static final ResourceLocation GUI_CONTROLS = ResourceLocation.fromNamespaceAndPath("sophisticatedcore", "textures/gui/gui_controls.png");

    @Inject(method = "renderTabBackground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", at = @At("HEAD"), cancellable = true)
    private static void glazuli$glassTab(GuiGraphics guiGraphics, int x, int y, int width, int height, CallbackInfo ci) {
        if (ContainerGlass.isEnabled() && !PackOverrides.texture(GUI_CONTROLS)) {
            GlassRenderer.drawPanel(guiGraphics, x, y, width, height, ContainerGlass.SURFACE);
            ci.cancel();
        }
    }
}
