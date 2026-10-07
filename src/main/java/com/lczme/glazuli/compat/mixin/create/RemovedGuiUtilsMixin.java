package com.lczme.glazuli.compat.mixin.create;

import com.lczme.glazuli.render.DarkText;
import com.lczme.glazuli.render.VanillaGlass;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create's own tooltip drawing (goggle overlay, value settings) on the same light glass as vanilla tooltips. It frames
 * the text box with seven gradient fills: the background body, then 1 px strips; the body becomes the glass.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.gui.RemovedGuiUtils", remap = false)
public abstract class RemovedGuiUtilsMixin {
    private static final String DRAW = "drawHoveringText(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;IIIIIIIILnet/minecraft/client/gui/Font;)V";

    @Unique
    private static boolean glazuli$glass;

    @Inject(method = DRAW, at = @At("HEAD"))
    private static void glazuli$beginDarkText(ItemStack stack, GuiGraphics guiGraphics, List<? extends FormattedText> lines,
                                                  int mouseX, int mouseY, int screenWidth, int screenHeight, int maxTextWidth,
                                                  int backgroundColor, int borderColorStart, int borderColorEnd, Font font,
                                                  CallbackInfo ci) {
        glazuli$glass = VanillaGlass.isEnabled();
        if (glazuli$glass) {
            DarkText.push();
        }
    }

    @Inject(method = DRAW, at = @At("RETURN"))
    private static void glazuli$endDarkText(ItemStack stack, GuiGraphics guiGraphics, List<? extends FormattedText> lines,
                                                int mouseX, int mouseY, int screenWidth, int screenHeight, int maxTextWidth,
                                                int backgroundColor, int borderColorStart, int borderColorEnd, Font font,
                                                CallbackInfo ci) {
        if (glazuli$glass) {
            glazuli$glass = false;
            DarkText.pop();
        }
    }

    @WrapOperation(method = DRAW, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fillGradient(IIIIIII)V"))
    private static void glazuli$glassBody(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int z, int colorFrom,
                                              int colorTo, Operation<Void> original) {
        if (!glazuli$glass) {
            original.call(guiGraphics, x1, y1, x2, y2, z, colorFrom, colorTo);
        } else if (x2 - x1 > 2 && y2 - y1 > 2) {
            // The goggle overlay draws its item icon first, at a higher z.
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, z);
            VanillaGlass.tooltip(guiGraphics, x1 + 3, y1 + 3, x2 - x1 - 6, y2 - y1 - 6, true);
            guiGraphics.pose().popPose();
        }
    }
}
