package com.lczme.liquidglass.dev;

import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.anim.Spring;
import com.lczme.liquidglass.anim.Timeline;
import com.lczme.liquidglass.render.GlassRenderer;
import com.lczme.liquidglass.render.GlassStyle;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/** Dev-only showcase of the glass renderer: plain panel, buttons, a PoseStack-scaled panel and a recaptured popup. */
public class GlassDemoScreen extends Screen {
    private static final int[] STRIPE_COLORS = {0xFFFF4D4D, 0xFFFFB13B, 0xFFFFE94D, 0xFF4DDB6B, 0xFF3BB4FF, 0xFF7A5CFF, 0xFFFF5CD6};

    private static final String[] BUTTON_LABELS = {"liquidglass.demo.button.play", "liquidglass.demo.button.options", "liquidglass.demo.button.quit"};

    @Nullable
    private final Screen parent;
    private final Timeline intro = new Timeline();
    private final Timeline.Track panelAlpha = intro.track(0.0F).to(0.0F, 0.45F, 1.0F, Easing.OUT_CUBIC);
    private final Timeline.Track panelScale = intro.track(0.92F).to(0.0F, 0.6F, 1.0F, Easing.OUT_BACK);
    private final Timeline.Track[] buttonRise = new Timeline.Track[BUTTON_LABELS.length];
    private final Spring[] hover = new Spring[BUTTON_LABELS.length];

    public GlassDemoScreen(@Nullable Screen parent) {
        super(Component.translatable("liquidglass.demo.title"));
        this.parent = parent;
        for (int i = 0; i < BUTTON_LABELS.length; i++) {
            buttonRise[i] = intro.track(0.0F).to(0.2F + 0.07F * i, 0.5F, 1.0F, Easing.OUT_BACK);
            hover[i] = Spring.bouncy(0.0F);
        }
    }

    @Override
    protected void init() {
        if (!intro.isStarted()) {
            intro.start();
        }
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

        // High-contrast content so blur and edge refraction are easy to judge.
        int stripeW = Math.max(6, width / 40);
        for (int i = 0; i * stripeW * 2 < width + height; i++) {
            int color = STRIPE_COLORS[i % STRIPE_COLORS.length];
            int sx = i * stripeW * 2;
            guiGraphics.fill(sx, height / 2 - 70, sx + stripeW, height / 2 - 62, color);
            guiGraphics.fill(sx + stripeW, height / 2 + 64, sx + stripeW * 2, height / 2 + 74, color);
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
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        float pw = Math.min(width - 40, 300);
        float ph = 150;
        float px = (width - pw) / 2.0F;
        float py = (height - ph) / 2.0F;

        float alpha = panelAlpha.value();
        float scale = panelScale.value();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(px + pw / 2.0F, py + ph / 2.0F, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.pose().translate(-(px + pw / 2.0F), -(py + ph / 2.0F), 0.0F);

        GlassRenderer.drawPanel(guiGraphics, px, py, pw, ph, GlassStyle.PANEL.withOpacity(alpha));
        if (isTextVisible(alpha)) {
            guiGraphics.drawString(font, title, (int) px + 16, (int) py + 14, withAlpha(0xFFFFFF, alpha), true);
            guiGraphics.drawString(font, Component.translatable("liquidglass.demo.subtitle"), (int) px + 16, (int) py + 28, withAlpha(0xE0E6F0, alpha), true);
        }

        float bw = (pw - 16 * 2 - 8 * 2) / 3.0F;
        float baseY = py + ph - 20 - 16;
        for (int i = 0; i < BUTTON_LABELS.length; i++) {
            float rise = buttonRise[i].value();
            float buttonAlpha = Math.min(alpha, Mth.clamp(rise, 0.0F, 1.0F));
            float bx = px + 16 + i * (bw + 8);
            float by = baseY + (1.0F - rise) * 14.0F;
            boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 20;
            // The middle button stays "hovered" so screenshots show the hover look.
            hover[i].setTarget(hovered || i == 1);
            float h = hover[i].value();
            float pop = 1.0F + 0.05F * h;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(bx + bw / 2.0F, by + 10.0F, 0.0F);
            guiGraphics.pose().scale(pop, pop, 1.0F);
            GlassRenderer.drawPanel(guiGraphics, -bw / 2.0F, -10.0F, bw, 20,
                    GlassStyle.lerp(GlassStyle.BUTTON, GlassStyle.BUTTON_HOVER, h).withOpacity(buttonAlpha));
            if (isTextVisible(buttonAlpha)) {
                guiGraphics.drawCenteredString(font, Component.translatable(BUTTON_LABELS[i]), 0, -4, withAlpha(0xFFFFFF, buttonAlpha));
            }
            guiGraphics.pose().popPose();
        }
        guiGraphics.pose().popPose();

        // Clear lens over the stripes to show refraction on a sharp backdrop.
        GlassRenderer.drawPanel(guiGraphics, px + pw - 66, py - 50, 44, 44, GlassStyle.LENS);

        // Scaled + translated through the PoseStack, as animations will do.
        float t = (Util.getMillis() % 4000L) / 4000.0F;
        float pulse = 1.1F + 0.1F * Mth.sin(t * Mth.TWO_PI);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(px + 60, py - 24, 0.0F);
        guiGraphics.pose().scale(pulse, pulse, 1.0F);
        GlassRenderer.drawPanel(guiGraphics, -40, -10, 80, 20, GlassStyle.BUTTON);
        guiGraphics.drawCenteredString(font, Component.translatable("liquidglass.demo.scaled"), 0, -4, 0xFFFFFFFF);
        guiGraphics.pose().popPose();

        // Popup above the main panel: re-capture so its glass blurs the panel beneath it.
        GlassRenderer.recaptureBackdrop(guiGraphics);
        float popW = 120;
        float popH = 60;
        float popX = px + pw - popW + 30;
        float popY = py + 44;
        GlassRenderer.drawPanel(guiGraphics, popX, popY, popW, popH, GlassStyle.DARK_PANEL.withCornerRadius(12.0F));
        guiGraphics.drawString(font, Component.translatable("liquidglass.demo.popup"), (int) popX + 12, (int) popY + 12, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, Component.translatable("liquidglass.demo.popup.detail"), (int) popX + 12, (int) popY + 26, 0xFFB8C0CC, true);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
