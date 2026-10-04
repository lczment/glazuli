package com.lczme.liquidglass.dev;

import com.lczme.liquidglass.render.GlassRenderer;
import com.lczme.liquidglass.render.GlassStyle;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Dev-only showcase of the glass renderer: plain panel, buttons, a PoseStack-scaled panel and a recaptured popup. */
public class GlassDemoScreen extends Screen {
    private static final int[] STRIPE_COLORS = {0xFFFF4D4D, 0xFFFFB13B, 0xFFFFE94D, 0xFF4DDB6B, 0xFF3BB4FF, 0xFF7A5CFF, 0xFFFF5CD6};

    @Nullable
    private final Screen parent;

    public GlassDemoScreen(@Nullable Screen parent) {
        super(Component.translatable("liquidglass.demo.title"));
        this.parent = parent;
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
        GlassRenderer.drawPanel(guiGraphics, px, py, pw, ph, GlassStyle.PANEL);
        guiGraphics.drawString(font, title, (int) px + 16, (int) py + 14, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, Component.translatable("liquidglass.demo.subtitle"), (int) px + 16, (int) py + 28, 0xFFE0E6F0, true);

        String[] labels = {"liquidglass.demo.button.play", "liquidglass.demo.button.options", "liquidglass.demo.button.quit"};
        float bw = (pw - 16 * 2 - 8 * 2) / 3.0F;
        float by = py + ph - 20 - 16;
        for (int i = 0; i < labels.length; i++) {
            float bx = px + 16 + i * (bw + 8);
            boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 20;
            GlassRenderer.drawPanel(guiGraphics, bx, by, bw, 20, hovered || i == 1 ? GlassStyle.BUTTON_HOVER : GlassStyle.BUTTON);
            guiGraphics.drawCenteredString(font, Component.translatable(labels[i]), (int) (bx + bw / 2), (int) by + 6, 0xFFFFFFFF);
        }

        // Clear lens over the stripes to show refraction on a sharp backdrop.
        GlassRenderer.drawPanel(guiGraphics, px + pw - 66, py - 50, 44, 44, GlassStyle.LENS);

        // Scaled + translated through the PoseStack, as animations will do.
        float t = (Util.getMillis() % 4000L) / 4000.0F;
        float scale = 1.1F + 0.1F * Mth.sin(t * Mth.TWO_PI);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(px + 60, py - 24, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
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
