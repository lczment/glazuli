package com.lczme.glazuli.compat.mixin.iris;

import com.lczme.glazuli.render.PackOverrides;
import com.lczme.glazuli.render.VanillaGlass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Iris draws its buttons and shader pack rows from its own widget sheet; they get the vanilla glass button look. */
@Mixin(targets = "net.irisshaders.iris.gui.GuiUtil", remap = false)
public abstract class IrisGuiUtilMixin {
    @Unique
    private static final ResourceLocation glazuli$WIDGETS = ResourceLocation.fromNamespaceAndPath("iris", "textures/gui/widgets.png");

    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private static void glazuli$glassButton(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean hovered,
                                                boolean disabled, CallbackInfo ci) {
        if (VanillaGlass.isEnabled() && !PackOverrides.texture(glazuli$WIDGETS)) {
            VanillaGlass.button(guiGraphics, x, y, width, height, hovered && !disabled);
            ci.cancel();
        }
    }
}
