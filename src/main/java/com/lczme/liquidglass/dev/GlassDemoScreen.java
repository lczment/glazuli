package com.lczme.liquidglass.dev;

import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.anim.Timeline;
import com.lczme.liquidglass.render.GlassRenderer;
import com.lczme.liquidglass.render.GlassStyle;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Dev-only showcase following the design spec: a film panel for content, real (glass-skinned) vanilla buttons
 * sitting directly on a high-contrast backdrop, a neutral glass lens, and a film popup over a re-captured backdrop.
 */
public class GlassDemoScreen extends Screen {
    private static final int[] STRIPE_COLORS = {0xFFFF4D4D, 0xFFFFB13B, 0xFFFFE94D, 0xFF4DDB6B, 0xFF3BB4FF, 0xFF7A5CFF, 0xFFFF5CD6};
    private static final float EASE_SECONDS = 0.38F;
    private static final int BUTTON_W = 90;
    private static final int BUTTON_GAP = 8;

    @Nullable
    private final Screen parent;
    private final Timeline intro = new Timeline();
    private final Timeline.Track panelAlpha = intro.track(0.0F).to(0.0F, EASE_SECONDS, 1.0F, Easing.SPEC_EASE);
    private final List<Timeline.Track> buttonRise = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private final List<Integer> buttonBaseY = new ArrayList<>();

    public GlassDemoScreen(@Nullable Screen parent) {
        super(Component.translatable("liquidglass.demo.title"));
        this.parent = parent;
        for (int i = 0; i < 3; i++) {
            buttonRise.add(intro.track(0.0F).to(0.08F + 0.04F * i, EASE_SECONDS, 1.0F, Easing.SPEC_EASE));
        }
    }

    @Override
    protected void init() {
        buttons.clear();
        buttonBaseY.clear();
        int rowW = BUTTON_W * 3 + BUTTON_GAP * 2;
        int x = (width - rowW) / 2;
        int y = height / 2 + 82;
        addButton(Button.builder(Component.translatable("liquidglass.demo.button.play"), b -> minecraft.setScreen(new SelectWorldScreen(this))), x, y);
        addButton(Button.builder(Component.translatable("liquidglass.demo.button.options"), b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))),
                x + BUTTON_W + BUTTON_GAP, y);
        addButton(Button.builder(CommonComponents.GUI_BACK, b -> onClose()), x + 2 * (BUTTON_W + BUTTON_GAP), y);
        if (!intro.isStarted()) {
            intro.start();
        }
    }

    private void addButton(Button.Builder builder, int x, int y) {
        buttons.add(addRenderableWidget(builder.bounds(x, y, BUTTON_W, 20).build()));
        buttonBaseY.add(y);
    }

    private static boolean isTextVisible(float alpha) {
        // Font treats colors with alpha below 4/255 as fully opaque, so near-invisible text must be skipped.
        return alpha > 0.02F;
    }

    private static int withAlpha(int rgb, float alpha) {
        return FastColor.ARGB32.color(Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F), rgb);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Panorama only: the vanilla menu blur + dirt overlay would hide what the glass is doing.
        renderPanorama(guiGraphics, partialTick);

        // High-contrast stage detail so refraction is visible (spec §4.1).
        int stripeW = Math.max(6, width / 40);
        for (int i = 0; i * stripeW * 2 < width + height; i++) {
            int color = STRIPE_COLORS[i % STRIPE_COLORS.length];
            int sx = i * stripeW * 2;
            guiGraphics.fill(sx, height / 2 - 70, sx + stripeW, height / 2 - 62, color);
            guiGraphics.fill(sx + stripeW, height / 2 + 86, sx + stripeW * 2, height / 2 + 98, color);
        }
        guiGraphics.fill(width / 2 - 2, 0, width / 2 + 2, height, 0xFFFFFFFF);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(width / 2.0F, height / 2.0F - 30.0F, 0.0F);
        guiGraphics.pose().scale(3.0F, 3.0F, 1.0F);
        Component big = Component.literal("LIQUID GLASS");
        guiGraphics.drawString(font, big, -font.width(big) / 2, 0, 0xFFFFFFFF, true);
        guiGraphics.pose().popPose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < buttons.size(); i++) {
            float rise = buttonRise.get(i).value();
            Button button = buttons.get(i);
            button.setY(Math.round(buttonBaseY.get(i) + (1.0F - rise) * 10.0F));
            button.setAlpha(rise);
            button.visible = isTextVisible(rise);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        float pw = Math.min(width - 40, 300);
        float ph = 120;
        float px = (width - pw) / 2.0F;
        float py = (height - ph) / 2.0F;
        float alpha = panelAlpha.value();
        GlassRenderer.drawPanel(guiGraphics, px, py, pw, ph, GlassStyle.FILM.withOpacity(alpha));
        if (isTextVisible(alpha)) {
            guiGraphics.drawString(font, title, (int) px + 16, (int) py + 14, withAlpha(0xFFFFFF, alpha), true);
            guiGraphics.drawString(font, Component.translatable("liquidglass.demo.subtitle"), (int) px + 16, (int) py + 28, withAlpha(0xE0E6F0, alpha), true);
        }

        // Neutral glass lens over the sharp stripes: refraction per spec §3.
        GlassRenderer.drawPanel(guiGraphics, px + pw - 66, py - 50, 44, 44, GlassStyle.NEUTRAL);

        // Popup above the panel: re-capture so the panel is part of what the popup film covers.
        GlassRenderer.recaptureBackdrop(guiGraphics);
        float popW = 120;
        float popH = 60;
        float popX = px + pw - popW + 30;
        float popY = py + 44;
        GlassRenderer.drawPanel(guiGraphics, popX, popY, popW, popH, GlassStyle.FILM);
        guiGraphics.drawString(font, Component.translatable("liquidglass.demo.popup"), (int) popX + 12, (int) popY + 12, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, Component.translatable("liquidglass.demo.popup.detail"), (int) popX + 12, (int) popY + 26, 0xFFB8C0CC, true);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
