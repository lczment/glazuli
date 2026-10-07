package com.lczme.glazuli.mixin;

import net.minecraft.client.gui.font.FontTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Larger glyph sheets for monochrome glyphs. The UI font rasterises CJK glyphs at the GUI scale, so a 256px sheet only
 * holds a few dozen of them; Chinese text then hops between sheets glyph by glyph, and every hop ends the batch and
 * costs a draw call. Colored (bitmap) sheets keep the vanilla size.
 */
@Mixin(FontTexture.class)
public abstract class FontTextureMixin {
    @Unique
    private static final int GLAZULI$MONO_SIZE = 2048;

    @Shadow
    @Final
    private boolean colored;

    @ModifyConstant(method = {"<init>", "dumpContents"}, constant = @Constant(intValue = 256))
    private int glazuli$sheetSize(int size) {
        return this.colored ? size : GLAZULI$MONO_SIZE;
    }

    @ModifyConstant(method = "add", constant = @Constant(floatValue = 256.0F))
    private float glazuli$sheetUvScale(float size) {
        return this.colored ? size : GLAZULI$MONO_SIZE;
    }
}
