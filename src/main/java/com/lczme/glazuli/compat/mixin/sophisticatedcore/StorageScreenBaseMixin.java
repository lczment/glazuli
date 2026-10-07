package com.lczme.glazuli.compat.mixin.sophisticatedcore;

import com.lczme.glazuli.render.ContainerGlass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sophisticated Storage and Backpacks render the container background from their own copy of the screen's render
 * method, so the window is brought into the glass background pass here as well.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase", remap = false)
public abstract class StorageScreenBaseMixin {
    @Inject(method = "renderSuper(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At(value = "INVOKE",
            target = "Lnet/p3pp3rf1y/sophisticatedcore/client/gui/StorageScreenBase;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"))
    private void glazuli$glassPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        ContainerGlass.beginBackground(screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize(), screen.getYSize());
    }

    @Inject(method = "renderSuper(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/p3pp3rf1y/sophisticatedcore/client/gui/StorageScreenBase;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"))
    private void glazuli$glassPanelEnd(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ContainerGlass.endBackground(guiGraphics);
    }
}
