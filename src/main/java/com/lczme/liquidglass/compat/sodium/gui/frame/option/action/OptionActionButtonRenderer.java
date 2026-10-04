package com.lczme.liquidglass.compat.sodium.gui.frame.option.action;

import com.mojang.blaze3d.systems.RenderSystem;
import com.lczme.liquidglass.compat.sodium.gui.layout.LayoutBounds;
import com.lczme.liquidglass.render.GlassUi;
import com.lczme.liquidglass.compat.sodium.gui.widget.BaseWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class OptionActionButtonRenderer {
    private static final int ICON_SIZE = 10;
    private static final int BACKGROUND = GlassUi.ROW_HOVER;
    private static final int BACKGROUND_HOVERED = 0x40FFFFFF;
    private static final int BACKGROUND_DISABLED = GlassUi.ROW;
    private static final int BORDER_FOCUSED = 0xFFFFFFFF;
    private static final float ICON_ALPHA_DISABLED = 0.4F;

    static LayoutBounds buttonBounds(LayoutBounds rowBounds, int buttonsFromRight) {
        int size = rowBounds.height();
        int x = rowBounds.getLimitX() - size * buttonsFromRight;

        return new LayoutBounds(x, rowBounds.y(), size, size);
    }

    static void render(GuiGraphics guiGraphics, ResourceLocation icon, LayoutBounds buttonBounds, int mouseX, int mouseY, boolean focused, boolean active) {
        boolean hovered = active && buttonBounds.contains(mouseX, mouseY);

        GlassUi.roundedFill(guiGraphics, buttonBounds.x() + 1, buttonBounds.y() + 1, buttonBounds.getLimitX() - 1, buttonBounds.getLimitY() - 1, 2,
                active ? (hovered ? BACKGROUND_HOVERED : BACKGROUND) : BACKGROUND_DISABLED);
        if (focused && BaseWidget.isKeyboardFocusVisible()) {
            BaseWidget.border(guiGraphics, buttonBounds.x(), buttonBounds.y(), buttonBounds.getLimitX(), buttonBounds.getLimitY(), BORDER_FOCUSED);
        }

        int iconX = buttonBounds.getCenterX() - ICON_SIZE / 2;
        int iconY = buttonBounds.getCenterY() - ICON_SIZE / 2;
        if (!active) {
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, ICON_ALPHA_DISABLED);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.blit(icon, iconX, iconY, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        RenderSystem.disableBlend();
        if (!active) {
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
