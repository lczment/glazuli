package com.lczme.glazuli.mixin;

import com.lczme.glazuli.cave.CaveBackground;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every menu without a world draws the panorama through here, so this is the one place the cave replaces it. */
@Mixin(PanoramaRenderer.class)
public abstract class PanoramaRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void glazuli$renderCave(GuiGraphics guiGraphics, int width, int height, float fade, float partialTick, CallbackInfo ci) {
        if (CaveBackground.isActive()) {
            CaveBackground.render(guiGraphics, width, height);
            ci.cancel();
        }
    }
}
