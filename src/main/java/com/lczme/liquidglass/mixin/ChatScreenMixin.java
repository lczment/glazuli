package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.render.DarkText;
import com.lczme.liquidglass.render.VanillaGlass;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The chat input bar as light glass with dark text. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void liquidglass$glassInput(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
        if (VanillaGlass.isEnabled()) {
            VanillaGlass.chatInput(guiGraphics, x1, y1, x2, y2);
        } else {
            original.call(guiGraphics, x1, y1, x2, y2, color);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void liquidglass$darkInput(EditBox input, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, Operation<Void> original) {
        boolean dark = VanillaGlass.isEnabled();
        if (dark) {
            DarkText.push();
        }
        try {
            original.call(input, guiGraphics, mouseX, mouseY, partialTick);
        } finally {
            if (dark) {
                DarkText.pop();
            }
        }
    }
}
