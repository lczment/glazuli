package com.lczme.glazuli.mixin;

import com.lczme.glazuli.cave.CaveBackground;
import com.lczme.glazuli.cave.ScenePickerScreen;
import com.lczme.glazuli.config.GlazuliConfig;
import com.lczme.glazuli.render.BlurFade;
import com.lczme.glazuli.title.TitleMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Unique
    private static final int GLAZULI$STYLE_BUTTON_MARGIN = 4;

    @Shadow
    private boolean fading;
    @Shadow
    private long fadeInStart;

    @Unique
    private TitleMenu glazuli$menu;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void glazuli$layout(CallbackInfo ci) {
        if (GlazuliConfig.SPEC.isLoaded()) {
            glazuli$addStyleButton();
        }
        if (!TitleMenu.isEnabled()) {
            glazuli$menu = null;
            return;
        }
        if (glazuli$menu == null) {
            glazuli$menu = new TitleMenu();
        }
        glazuli$menu.layout((Screen) (Object) this);
    }

    /** Switches classic mode: every screen and the font back to vanilla, the cave kept. */
    @Unique
    private void glazuli$addStyleButton() {
        Component label = Component.translatable(GlazuliConfig.isClassic() ? "glazuli.title.glass" : "glazuli.title.classic");
        int width = this.font.width(label) + 16;
        int x = this.width - width - GLAZULI$STYLE_BUTTON_MARGIN;
        this.addRenderableWidget(Button.builder(label, button -> {
            GlazuliConfig.CLASSIC_UI.set(!GlazuliConfig.CLASSIC_UI.get());
            GlazuliConfig.CLASSIC_UI.save();
            // Not while the widget list is being iterated (the click may fire from a render pass).
            this.minecraft.tell(this::rebuildWidgets);
        }).bounds(x, GLAZULI$STYLE_BUTTON_MARGIN, width, 20).build());
        if (CaveBackground.isActive()) {
            Component scenes = Component.translatable("glazuli.title.scenes");
            int scenesWidth = this.font.width(scenes) + 16;
            this.addRenderableWidget(Button.builder(scenes, button -> this.minecraft.setScreen(new ScenePickerScreen(this)))
                    .bounds(x - scenesWidth - GLAZULI$STYLE_BUTTON_MARGIN, GLAZULI$STYLE_BUTTON_MARGIN, scenesWidth, 20).build());
        }
    }

    /**
     * Drives vanilla's title fade with our entrance: while fading, vanilla's opacity is
     * {@code clampedMap((now - fadeInStart) / 2000, 0.5, 1, 0, 1)}, so choosing {@code fadeInStart} sets it exactly.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void glazuli$beforeRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (glazuli$menu == null) {
            return;
        }
        glazuli$menu.beforeRender();
        float alpha = glazuli$menu.chromeAlpha();
        fading = alpha < 1.0F;
        if (fading) {
            fadeInStart = Util.getMillis() - (long) (1000.0F + 1000.0F * alpha);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;fadeWidgets(F)V"))
    private void glazuli$afterFade(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (glazuli$menu != null) {
            glazuli$menu.afterVanillaFade();
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
    private void glazuli$logoEntrance(LogoRenderer logo, GuiGraphics guiGraphics, int width, float alpha, Operation<Void> original) {
        if (glazuli$menu == null || !glazuli$menu.drawLogo(guiGraphics, width)) {
            original.call(logo, guiGraphics, width, alpha);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;renderPanorama(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
    private void glazuli$drawBackdrop(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (BlurFade.isFadingOut()) {
            guiGraphics.flush();
            BlurFade.fadeOut(() -> this.renderBlurredBackground(partialTick));
        }
        if (glazuli$menu != null) {
            glazuli$menu.drawBackdrop(guiGraphics, this.width, this.height);
        }
    }
}
