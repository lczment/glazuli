package com.lczme.glazuli.compat.mixin.jei;

import com.lczme.glazuli.compat.jei.JeiGlass;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fixed-size JEI sprites: recipe category tabs and item slots. The unmasked overload delegates here. */
@Mixin(targets = "mezz.jei.common.gui.elements.DrawableSprite", remap = false)
public abstract class JeiDrawableSpriteMixin {
    @Shadow
    @Final
    private Supplier<TextureAtlasSprite> spriteSupplier;

    @Shadow
    public abstract int getWidth();

    @Shadow
    public abstract int getHeight();

    @Inject(method = "draw(Lnet/minecraft/client/gui/GuiGraphics;IIIIII)V", at = @At("HEAD"), cancellable = true)
    private void glazuli$drawGlass(GuiGraphics guiGraphics, int x, int y, int maskTop, int maskBottom, int maskLeft,
                                       int maskRight, CallbackInfo ci) {
        // Partially masked sprites are progress bars (arrows, flames); those keep JEI's art.
        if ((maskTop | maskBottom | maskLeft | maskRight) == 0
                && JeiGlass.draw(guiGraphics, this.spriteSupplier.get().contents().name(), x, y, this.getWidth(), this.getHeight())) {
            ci.cancel();
        }
    }
}
