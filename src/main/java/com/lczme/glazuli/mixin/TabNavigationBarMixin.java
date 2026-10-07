package com.lczme.glazuli.mixin;

import com.google.common.collect.ImmutableList;
import com.lczme.glazuli.render.TabSlide;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the tab manager which way a tab switch goes, and drives its slide while the bar is drawn (before the tab). */
@Mixin(TabNavigationBar.class)
public abstract class TabNavigationBarMixin {
    @Shadow
    @Final
    private TabManager tabManager;
    @Shadow
    @Final
    private ImmutableList<TabButton> tabButtons;

    @Shadow
    private int currentTabIndex() {
        throw new AssertionError();
    }

    @Inject(method = "selectTab", at = @At("HEAD"))
    private void glazuli$directionByIndex(int index, boolean playClickSound, CallbackInfo ci) {
        glazuli$direction(index);
    }

    @Inject(method = "setFocused(Lnet/minecraft/client/gui/components/events/GuiEventListener;)V", at = @At("HEAD"))
    private void glazuli$directionByButton(GuiEventListener listener, CallbackInfo ci) {
        if (listener instanceof TabButton button) {
            glazuli$direction(tabButtons.indexOf(button));
        }
    }

    @Unique
    private void glazuli$direction(int index) {
        int current = currentTabIndex();
        if (index >= 0 && current >= 0 && index != current) {
            ((TabSlide) tabManager).glazuli$setSlideDirection(index - current);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void glazuli$animateTabSlide(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ((TabSlide) tabManager).glazuli$animateSlide();
    }
}
