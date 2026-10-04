package com.lczme.liquidglass.title;

import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.anim.Timeline;
import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.lczme.liquidglass.cave.CaveBackground;
import com.lczme.liquidglass.intro.IntroState;
import com.lczme.liquidglass.render.GlassButtons;
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
    /** Bottom of vanilla's logo + "Java Edition" badge ({@code LogoRenderer}, drawn at y = 30). */
    private static final float VANILLA_LOGO_BOTTOM = 82.0F;

    /** Vertical centre of the logo + badge, the pivot of the logo entrance. */
    private static final float LOGO_PIVOT_Y = 55.0F;
    // LabyMod-style logo entrance: the logo settles from slightly larger and higher while fading in, then the menu rises.
    private static final float LOGO_START_SCALE = 1.18F;
    private static final float LOGO_DROP = 14.0F;
    private static final float LOGO_SECONDS = 1.1F;
    private static final float FADE_SECONDS = 0.6F;
    private static final float BUTTONS_DELAY = 0.3F;

    private final Timeline entrance = new Timeline();
    private final Timeline.Track logoAlpha = entrance.track(0.0F).to(0.0F, FADE_SECONDS, 1.0F, Easing.SPEC_EASE);
    private final Timeline.Track logoIn = entrance.track(0.0F).to(0.0F, LOGO_SECONDS, 1.0F, Easing.OUT_EXPO);
    private final List<Timeline.Track> rises = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private final boolean continuesIntro;
    private boolean settled;

    public TitleMenu() {
        continuesIntro = IntroState.isPlaying();
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
        float logoBottom = VANILLA_LOGO_BOTTOM;
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
            rises.add(entrance.track(0.0F).to(BUTTONS_DELAY + STAGGER * i, EASE_SECONDS, 1.0F, Easing.SPEC_EASE));
        }
        if (settled) {
            applyEntrance(1.0F);
        }
    }

    /** Called at the start of {@code TitleScreen#render}, before widgets are drawn. */
    public void beforeRender() {
        boolean introReady = !continuesIntro || IntroState.isDone() || IntroState.outroProgress() >= 0.35F;
        if (!entrance.isStarted() && introReady && CaveBackground.titleEntranceReady()) {
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

    /**
     * Opacity for everything vanilla draws itself (Minecraft logo, splash, branding, unrecognized widgets); applied
     * through vanilla's own title fade so all of it appears together with our entrance.
     */
    public float chromeAlpha() {
        return settled ? 1.0F : logoAlpha.value();
    }

    /**
     * Pushes the logo entrance transform (shared by the logo and the splash text so they move together); returns
     * false, without pushing, once it's at rest. Pair a true result with popPose.
     */
    public boolean pushLogoTransform(GuiGraphics guiGraphics, int width) {
        float t = settled ? 1.0F : logoIn.value();
        if (t >= 1.0F) {
            return false;
        }
        float scale = Mth.lerp(t, LOGO_START_SCALE, 1.0F);
        float cx = width * 0.5F;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(cx, LOGO_PIVOT_Y - (1.0F - t) * LOGO_DROP, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.pose().translate(-cx, -LOGO_PIVOT_Y, 0.0F);
        return true;
    }

    /** Called after vanilla's fade has set every widget's alpha: puts our staggered entrance back on top. */
    public void afterVanillaFade() {
        applyEntrance(settled ? 1.0F : 0.0F);
    }

    private void place(AbstractWidget widget, int x, int y) {
        widget.setPosition(x, y);
        GlassButtons.useNeutral(widget);
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
