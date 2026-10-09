package com.lczme.glazuli.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("fontManager")
    FontManager glazuli$getFontManager();

    @Invoker("runTick")
    void glazuli$runTick(boolean renderLevel);
}
