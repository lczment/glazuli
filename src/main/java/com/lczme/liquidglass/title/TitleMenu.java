package com.lczme.liquidglass.title;

import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.anim.Timeline;
import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.lczme.liquidglass.cave.CaveBackground;
import com.lczme.liquidglass.intro.IntroState;
import com.lczme.liquidglass.render.GlassButtons;
import com.lczme.liquidglass.render.PackOverrides;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.LogoRenderer;
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

    // LabyMod-style logo entrance: the letters of the vanilla logo drop in one by one with a small bounce, then the
    // edition badge, splash and menu follow.
    /** Letter boundaries of the vanilla logo in its 256-unit width (seams between the stone letters M I N E C R A F T). */
    private static final int[] LETTER_EDGES = {0, 42, 57, 89, 117, 145, 174, 203, 232, 256};
    private static final int[] WHOLE_LOGO = {0, 256};
    private static final float LETTER_DROP = 36.0F;
    private static final float LETTER_STAGGER = 0.07F;
    private static final float LETTER_SECONDS = 0.55F;
    private static final float LETTER_FADE_SECONDS = 0.18F;
    private static final float LETTERS_END = LETTER_STAGGER * (LETTER_EDGES.length - 2) + LETTER_SECONDS;
    private static final float BADGE_DELAY = LETTERS_END - 0.25F;
    private static final float FADE_SECONDS = 0.45F;
    private static final float BUTTONS_DELAY = LETTERS_END - 0.35F;
    private static final int LOGO_Y = 30;
    private static final float MIN_CHROME_ALPHA = 0.02F;

    private final Timeline entrance = new Timeline();
    private final Timeline.Track logoAlpha = entrance.track(0.0F).to(BADGE_DELAY, FADE_SECONDS, 1.0F, Easing.SPEC_EASE);
    private final Timeline.Track[] letterDrop = new Timeline.Track[LETTER_EDGES.length - 1];
    private final Timeline.Track[] letterAlpha = new Timeline.Track[LETTER_EDGES.length - 1];

    {
        for (int i = 0; i < letterDrop.length; i++) {
            letterDrop[i] = entrance.track(0.0F).to(LETTER_STAGGER * i, LETTER_SECONDS, 1.0F, Easing.OUT_BACK);
            letterAlpha[i] = entrance.track(0.0F).to(LETTER_STAGGER * i, LETTER_FADE_SECONDS, 1.0F, Easing.OUT_CUBIC);
        }
    }
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
        boolean introReady = !continuesIntro || IntroState.isDone() || IntroState.outroProgress() >= 0.6F;
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
        if (settled) {
            return 1.0F;
        }
        // Vanilla skips the logo (and everything else) below ~4/255, but the letters must drop in before the rest.
        return entrance.isStarted() ? Math.max(MIN_CHROME_ALPHA, logoAlpha.value()) : 0.0F;
    }

    /**
     * Draws the logo during the entrance, letter by letter (same textures and placement as {@code LogoRenderer});
     * returns false once the entrance is over so vanilla draws it again.
     */
    public boolean drawLogo(GuiGraphics guiGraphics, int width) {
        if (settled) {
            return false;
        }
        int x0 = width / 2 - 128;
        // A resource pack's logo has its own letters, so it drops in as one piece.
        boolean packLogo = PackOverrides.texture(LogoRenderer.MINECRAFT_LOGO);
        int[] edges = packLogo ? WHOLE_LOGO : LETTER_EDGES;
        RenderSystem.enableBlend();
        for (int piece = 0; piece < edges.length - 1; piece++) {
            int i = packLogo ? letterDrop.length / 2 : piece;
            float alpha = letterAlpha[i].value();
            if (alpha <= 0.0F) {
                continue;
            }
            int u0 = edges[piece];
            int u1 = edges[piece + 1];
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, -(1.0F - letterDrop[i].value()) * LETTER_DROP, 0.0F);
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            guiGraphics.blit(LogoRenderer.MINECRAFT_LOGO, x0 + u0, LOGO_Y, u1 - u0, 44, u0, 0.0F, u1 - u0, 44, 256, 64);
            guiGraphics.pose().popPose();
        }
        float badge = logoAlpha.value();
        if (badge > 0.0F) {
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, badge);
            guiGraphics.blit(LogoRenderer.MINECRAFT_EDITION, width / 2 - 64, Math.round(LOGO_Y + 37 + (1.0F - badge) * 6.0F),
                    0.0F, 0.0F, 128, 14, 128, 16);
        }
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
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
