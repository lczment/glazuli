package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Toasts float a few pixels off the top-right corner instead of touching the screen edges. */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public abstract class ToastInstanceMixin {
    @Unique
    private static final float LIQUIDGLASS$MARGIN = 5.0F;

    @Shadow
    @Final
    private Toast toast;

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void liquidglass$inset(PoseStack pose, float x, float y, float z, Operation<Void> original, int width,
                                   GuiGraphics guiGraphics) {
        if (LiquidGlassConfig.isClassic()) {
            original.call(pose, x, y, z);
            return;
        }
        float visibility = toast.width() > 0 ? (width - x) / toast.width() : 1.0F;
        original.call(pose, x - LIQUIDGLASS$MARGIN * visibility, y + LIQUIDGLASS$MARGIN, z);
    }
}
