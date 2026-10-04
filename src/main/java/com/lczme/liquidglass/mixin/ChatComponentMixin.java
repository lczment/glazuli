package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.render.DarkText;
import com.lczme.liquidglass.render.VanillaGlass;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat lines on rows of light glass instead of black, with dark text. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Unique
    private boolean liquidglass$glass;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V", at = @At("HEAD"))
    private void liquidglass$beginDarkText(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        liquidglass$glass = VanillaGlass.isEnabled();
        if (liquidglass$glass) {
            DarkText.push();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V", at = @At("RETURN"))
    private void liquidglass$endDarkText(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        if (liquidglass$glass) {
            DarkText.pop();
        }
    }

    @WrapOperation(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void liquidglass$glassLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (liquidglass$glass && (color & 0xFFFFFF) == 0) {
            VanillaGlass.chatLine(guiGraphics, x1, y1, x2, y2, color);
        } else {
            original.call(guiGraphics, x1, y1, x2, y2, color);
        }
    }
}
