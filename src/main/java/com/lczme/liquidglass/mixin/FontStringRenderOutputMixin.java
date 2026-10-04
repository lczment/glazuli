package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.render.DarkText;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Style colours (formatting codes, coloured components) inside a {@link DarkText} scope. */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class FontStringRenderOutputMixin {
    @ModifyExpressionValue(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/TextColor;getValue()I"))
    private int liquidglass$darkStyleColor(int color) {
        return DarkText.remap(color);
    }
}
