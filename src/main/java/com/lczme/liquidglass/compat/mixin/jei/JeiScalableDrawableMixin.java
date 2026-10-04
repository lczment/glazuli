package com.lczme.liquidglass.compat.mixin.jei;

import com.lczme.liquidglass.compat.jei.JeiGlass;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every JEI background, button and the search field go through this nine-slice drawable. */
@Mixin(targets = "mezz.jei.common.gui.elements.ScalableDrawable", remap = false)
public abstract class JeiScalableDrawableMixin {
    @Shadow
    @Final
    private Supplier<TextureAtlasSprite> spriteSupplier;

    @Inject(method = "draw(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", at = @At("HEAD"), cancellable = true)
    private void liquidglass$drawGlass(GuiGraphics guiGraphics, int x, int y, int width, int height, CallbackInfo ci) {
        if (JeiGlass.draw(guiGraphics, this.spriteSupplier.get().contents().name(), x, y, width, height)) {
            ci.cancel();
        }
    }
}
