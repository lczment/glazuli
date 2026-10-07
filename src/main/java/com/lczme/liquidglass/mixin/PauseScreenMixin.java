package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.lczme.liquidglass.config.LiquidGlassConfig.InGameUi;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    @Unique
    private static final int LIQUIDGLASS$MARGIN = 4;

    @Shadow
    @Final
    private boolean showPauseMenu;

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    /** Switches the in-game style only; the title screen's button keeps the menus' own. */
    @Inject(method = "init", at = @At("TAIL"))
    private void liquidglass$addStyleButton(CallbackInfo ci) {
        if (!this.showPauseMenu || !LiquidGlassConfig.SPEC.isLoaded()) {
            return;
        }
        boolean classic = LiquidGlassConfig.isClassic();
        Component label = Component.translatable(classic ? "liquidglass.pause.glass" : "liquidglass.pause.classic");
        int width = this.font.width(label) + 16;
        this.addRenderableWidget(Button.builder(label, button -> {
            LiquidGlassConfig.IN_GAME_UI.set(classic ? InGameUi.LIQUID_GLASS : InGameUi.VANILLA);
            LiquidGlassConfig.IN_GAME_UI.save();
            this.minecraft.tell(this::rebuildWidgets);
        }).bounds(this.width - width - LIQUIDGLASS$MARGIN, LIQUIDGLASS$MARGIN, width, 20).build());
    }
}
