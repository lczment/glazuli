package com.lczme.glazuli.mixin;

import com.lczme.glazuli.render.DarkText;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Base colour and shadow of all text drawn inside a {@link DarkText} scope. */
@Mixin(Font.class)
public abstract class FontMixin {
    private static final String DRAW_STRING = "drawInternal(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I";
    private static final String DRAW_SEQUENCE = "drawInternal(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I";

    @ModifyVariable(method = {DRAW_STRING, DRAW_SEQUENCE}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int glazuli$darkColor(int color) {
        return DarkText.remap(color);
    }

    @ModifyVariable(method = {DRAW_STRING, DRAW_SEQUENCE}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean glazuli$noShadow(boolean dropShadow) {
        return dropShadow && !DarkText.isActive();
    }
}
