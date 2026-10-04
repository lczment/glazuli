package com.lczme.liquidglass.title;

import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.anim.Timeline;
import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.lczme.liquidglass.intro.IntroLogo;
import com.lczme.liquidglass.intro.IntroState;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;

/**
 * Liquid Glass layout and animation for the vanilla title screen, driven by {@code TitleScreenMixin}.
 * <p>
 * The vanilla widgets are kept and only repositioned, so buttons other mods add to the title screen keep working.
 * Widgets that aren't recognized stay where their owner put them. One instance lives per title screen instance, so
 * returning from a sub-screen (which re-runs {@code init}) doesn't replay the entrance animation.
 */
public final class TitleMenu {
    private static final int BUTTON_WIDTH = 200;
    private static final int ROW = 24;
    private static final int ICON = 20;
    private static final int GAP = 4;
    private static final float RISE = 10.0F;
    // Spec §6: page state changes use --ease over ~0.35-0.40 s.
    private static final float EASE_SECONDS = 0.38F;
    private static final float STAGGER = 0.04F;

    private final Timeline entrance = new Timeline();
    private final Timeline.Track logoAlpha = entrance.track(0.0F).to(0.0F, EASE_SECONDS, 1.0F, Easing.SPEC_EASE);
    private final List<Timeline.Track> rises = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private final boolean continuesIntro;
    private boolean settled;

    public TitleMenu() {
        continuesIntro = IntroState.isPlaying();
        if (continuesIntro) {
            IntroState.requestLogoHandoff();
        }
    }

    public static boolean isEnabled() {
        return LiquidGlassConfig.SPEC.isLoaded() && LiquidGlassConfig.CUSTOM_TITLE_SCREEN.get();
    }

    /** Called at the end of {@code TitleScreen#init}. */
    public void layout(Screen screen) {
        slots.clear();
        List<AbstractWidget> main = new ArrayList<>();
        AbstractWidget options = null;
        AbstractWidget quit = null;
        AbstractWidget language = null;
        AbstractWidget accessibility = null;
        for (GuiEventListener listener : screen.children()) {
            if (!(listener instanceof AbstractWidget widget)) {
                continue;
            }
            switch (translationKey(widget)) {
                case "menu.singleplayer", "menu.multiplayer", "menu.online", "fml.menu.mods", "menu.playdemo", "menu.resetdemo" -> main.add(widget);
                case "menu.options" -> options = widget;
                case "menu.quit" -> quit = widget;
                case "options.language" -> language = widget;
                case "options.accessibility", "accessibility.onboarding.accessibility.button" -> accessibility = widget;
                case null, default -> {
                }
            }
        }

        int rows = main.size() + (options != null || quit != null || language != null || accessibility != null ? 1 : 0);
        if (rows == 0) {
            return;
        }
        int height = screen.height;
        int columnH = rows * ROW - GAP + (main.isEmpty() || rows == main.size() ? 0 : GAP);
        float logoBottom = IntroState.menuLogoCenterY(height) + IntroState.menuLogoHeight(height) * 0.5F;
        // Keep clear of the two branding lines vanilla/NeoForge draw in the bottom corners.
        int x = (screen.width - BUTTON_WIDTH) / 2;
        int y = Math.round(Math.max(4.0F, Math.min(logoBottom + 26.0F, height - 34.0F - columnH)));
        for (AbstractWidget widget : main) {
            widget.setWidth(BUTTON_WIDTH);
            place(widget, x, y);
            y += ROW;
        }
        if (rows > main.size()) {
            if (!main.isEmpty()) {
                y += GAP;
            }
            int textWidth = (BUTTON_WIDTH - 2 * (ICON + GAP) - GAP) / 2;
            if (language != null) {
                place(language, x, y);
            }
            if (options != null) {
                options.setWidth(textWidth);
                place(options, x + ICON + GAP, y);
            }
            if (quit != null) {
                quit.setWidth(textWidth);
                place(quit, x + ICON + GAP + textWidth + GAP, y);
            }
            if (accessibility != null) {
                place(accessibility, x + BUTTON_WIDTH - ICON, y);
            }
        }

        while (rises.size() < slots.size()) {
            int i = rises.size();
            rises.add(entrance.track(0.0F).to(0.08F + STAGGER * i, EASE_SECONDS, 1.0F, Easing.SPEC_EASE));
        }
        if (settled) {
            applyEntrance(1.0F);
        }
    }

    /** Called at the start of {@code TitleScreen#render}, before widgets are drawn. */
    public void beforeRender() {
        if (!entrance.isStarted() && (!continuesIntro || IntroState.isDone() || IntroState.outroProgress() >= 0.35F)) {
            entrance.start();
        }
        if (!settled) {
            applyEntrance(0.0F);
            if (entrance.isFinished()) {
                settled = true;
                applyEntrance(1.0F);
            }
        }
    }

    /**
     * Called right after the panorama is drawn. The dimmed panorama is the backdrop the glass buttons refract; they sit
     * directly on it (spec: no card between glass and backdrop, a uniform card would hide the refraction).
     */
    public void drawBackdrop(GuiGraphics guiGraphics, int width, int height) {
        guiGraphics.fillGradient(0, 0, width, height, 0x300B0D12, 0x900B0D12);
    }

    /** Replaces the vanilla "Minecraft" logo. */
    public void drawLogo(GuiGraphics guiGraphics, int width, int height) {
        if (IntroState.isPlaying()) {
            // The intro overlay still draws the logo on top of this screen.
            return;
        }
        float alpha = continuesIntro ? 1.0F : logoAlpha.value();
        float logoHeight = IntroState.menuLogoHeight(height);
        IntroLogo.prepare(logoHeight);
        guiGraphics.flush();
        IntroLogo.draw(guiGraphics.pose().last().pose(), width * 0.5F, IntroState.menuLogoCenterY(height), logoHeight, alpha, 2.0F);
    }

    private void place(AbstractWidget widget, int x, int y) {
        widget.setPosition(x, y);
        slots.add(new Slot(widget, y));
    }

    /** Moves widgets along their entrance animation; {@code force} = 1 snaps them to their final state. */
    private void applyEntrance(float force) {
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            float rise = force >= 1.0F ? 1.0F : rises.get(i).value();
            float alpha = Mth.clamp(rise, 0.0F, 1.0F);
            slot.widget.setY(Math.round(slot.baseY + (1.0F - rise) * RISE));
            slot.widget.setAlpha(alpha);
            // Widget text with alpha below 4/255 renders opaque, so hide nearly transparent widgets instead.
            slot.widget.visible = alpha > 0.02F;
        }
    }

    @Nullable
    private static String translationKey(AbstractWidget widget) {
        return widget.getMessage().getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    private record Slot(AbstractWidget widget, int baseY) {
    }
}
