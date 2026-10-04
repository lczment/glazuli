package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.render.VanillaGlass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
    @Inject(method = "drawBar(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;)V", at = @At("HEAD"), cancellable = true)
    private void liquidglass$glassBar(GuiGraphics guiGraphics, int x, int y, BossEvent event, CallbackInfo ci) {
        if (VanillaGlass.isEnabled() && !VanillaGlass.bossBarOverridden()) {
            VanillaGlass.bossBar(guiGraphics, x, y, event);
            ci.cancel();
        }
    }
}
