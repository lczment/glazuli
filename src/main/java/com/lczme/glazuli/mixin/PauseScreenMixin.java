package com.lczme.glazuli.mixin;

import com.lczme.glazuli.config.GlazuliConfig;
import com.lczme.glazuli.config.GlazuliConfig.InGameUi;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    private static final int GLAZULI$GAP = 4;
    @Unique
    private static final int GLAZULI$SIZE = 20;

    @Shadow
    @Final
    private boolean showPauseMenu;

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    /**
     * An icon button beside the menu's right column, mirroring Create's config button on the left; it switches the
     * in-game style only, the title screen's button keeps the menus' own.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void glazuli$addStyleButton(CallbackInfo ci) {
        if (!this.showPauseMenu || !GlazuliConfig.SPEC.isLoaded()) {
            return;
        }
        AbstractWidget anchor = glazuli$find("menu.reportBugs");
        if (anchor == null) {
            anchor = glazuli$find("menu.options");
        }
        int x = anchor != null ? anchor.getRight() + GLAZULI$GAP : this.width - GLAZULI$SIZE - GLAZULI$GAP;
        int y = anchor != null ? anchor.getY() : GLAZULI$GAP;

        boolean classic = GlazuliConfig.isClassic();
        Component action = Component.translatable(classic ? "glazuli.pause.glass" : "glazuli.pause.classic");
        ItemStack icon = new ItemStack(classic ? Items.GRASS_BLOCK : Items.LIGHT_BLUE_STAINED_GLASS);
        Button button = new Button(x, y, GLAZULI$SIZE, GLAZULI$SIZE, action, pressed -> {
            GlazuliConfig.IN_GAME_UI.set(classic ? InGameUi.LIQUID_GLASS : InGameUi.VANILLA);
            GlazuliConfig.IN_GAME_UI.save();
            this.minecraft.tell(this::rebuildWidgets);
        }, narration -> narration.get()) {
            @Override
            protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
                guiGraphics.renderItem(icon, getX() + 2, getY() + 2);
            }

            @Override
            public void renderString(GuiGraphics guiGraphics, Font font, int color) {
            }
        };
        button.setTooltip(Tooltip.create(action));
        this.addRenderableWidget(button);
    }

    @Unique
    @Nullable
    private AbstractWidget glazuli$find(String key) {
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget && widget.getMessage().getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals(key)) {
                return widget;
            }
        }
        return null;
    }
}
