package com.lczme.glazuli.mixin;

import com.lczme.glazuli.anim.AnimClock;
import com.lczme.glazuli.anim.Easing;
import com.lczme.glazuli.render.TabSlide;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slides and fades a newly selected tab's widgets in. They are moved for real (so clicks follow them), relative to where
 * the tab's layout put them; a new layout puts them back at rest.
 */
@Mixin(TabManager.class)
public abstract class TabManagerMixin implements TabSlide {
    @Shadow
    @Nullable
    private Tab currentTab;

    @Shadow
    @Nullable
    public abstract Tab getCurrentTab();

    @Unique
    @Nullable
    private Tab glazuli$previous;
    @Unique
    private int glazuli$direction = 1;
    @Unique
    private double glazuli$start = Double.NaN;
    @Unique
    private int glazuli$applied;

    @Override
    public void glazuli$setSlideDirection(int direction) {
        glazuli$direction = direction == 0 ? 1 : Integer.signum(direction);
    }

    @Inject(method = "setCurrentTab", at = @At("HEAD"))
    private void glazuli$rememberPrevious(Tab tab, boolean playClickSound, CallbackInfo ci) {
        glazuli$previous = currentTab;
    }

    @Inject(method = "setCurrentTab", at = @At("TAIL"))
    private void glazuli$startSlide(Tab tab, boolean playClickSound, CallbackInfo ci) {
        Tab previous = glazuli$previous;
        glazuli$previous = null;
        if (previous == null || previous == tab) {
            return;
        }
        glazuli$applied = 0;
        glazuli$start = AnimClock.now();
        glazuli$animateSlide();
    }

    @Inject(method = "setTabArea", at = @At("TAIL"))
    private void glazuli$relayout(ScreenRectangle tabArea, CallbackInfo ci) {
        glazuli$applied = 0;
    }

    @Override
    public void glazuli$animateSlide() {
        Tab tab = getCurrentTab();
        if (tab == null || Double.isNaN(glazuli$start)) {
            return;
        }
        float t = Mth.clamp((float) ((AnimClock.now() - glazuli$start) / SECONDS), 0.0F, 1.0F);
        float e = Easing.SPEC_EASE.apply(t);
        int offset = Math.round(glazuli$direction * DISTANCE * (1.0F - e));
        // Text below ~4/255 alpha renders opaque.
        float alpha = t >= 1.0F ? 1.0F : Math.max(0.05F, Mth.clamp(e * 1.3F, 0.0F, 1.0F));
        int delta = offset - glazuli$applied;
        glazuli$applied = offset;
        tab.visitChildren(widget -> {
            widget.setX(widget.getX() + delta);
            widget.setAlpha(alpha);
        });
        if (t >= 1.0F) {
            glazuli$start = Double.NaN;
        }
    }
}
