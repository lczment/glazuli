package com.lczme.glazuli.mixin;

import com.lczme.glazuli.font.FontPack;
import com.lczme.glazuli.font.FontScale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Sends text in the default font to the UI font while it's in use; both stay loaded, so toggling is instant. */
@Mixin(FontManager.class)
public abstract class FontManagerMixin {
    @Shadow
    @Final
    private Map<ResourceLocation, FontSet> fontSets;

    @ModifyVariable(method = "getFontSetCached", at = @At("HEAD"), argsOnly = true)
    private ResourceLocation glazuli$uiFont(ResourceLocation id) {
        return id.equals(Minecraft.DEFAULT_FONT) && FontScale.isEnabled() && this.fontSets.containsKey(FontPack.UI_FONT_ID)
                ? FontPack.UI_FONT_ID : id;
    }
}
