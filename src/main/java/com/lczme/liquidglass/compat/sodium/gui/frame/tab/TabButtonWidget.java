package com.lczme.liquidglass.compat.sodium.gui.frame.tab;

import com.lczme.liquidglass.compat.sodium.config.ReeseSodiumOptionsConfig;
import com.lczme.liquidglass.compat.sodium.gui.layout.LayoutBounds;
import com.lczme.liquidglass.compat.sodium.gui.theme.GuiThemes;
import com.lczme.liquidglass.compat.sodium.gui.theme.GuiTheme;
import com.lczme.liquidglass.render.GlassUi;
import com.lczme.liquidglass.compat.sodium.gui.widget.BaseWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

final class TabButtonWidget extends BaseWidget {
    private static final int TEXT_X = 10;
    private static final int PILL_INSET_X = 4;

    private final Tab<?> tab;
    private final GuiTheme theme;
    private final Runnable action;
    private boolean selected;

    TabButtonWidget(LayoutBounds dim, Tab<?> tab, boolean selected, Runnable action) {
        super(dim);
        this.tab = tab;
        this.selected = selected;
        this.action = action;
        this.theme = ReeseSodiumOptionsConfig.config().isColorThemes()
                ? GuiThemes.fromSodium(tab.getModOptions().theme())
                : GuiThemes.DEFAULT_BUTTON;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.hovered = this.isMouseOver(mouseX, mouseY);
        float hover = this.hoverAmount(this.hovered);
        int x1 = this.getX() + PILL_INSET_X;
        int x2 = this.getLimitX() - PILL_INSET_X;
        if (this.selected) {
            GlassUi.panel(guiGraphics, x1, this.getY() + 1, x2 - x1, this.getHeight() - 2, GlassUi.SELECTION.withHover(hover));
        } else {
            GlassUi.roundedFill(guiGraphics, x1, this.getY() + 1, x2, this.getLimitY() - 1, 2, (int) (hover * 0x1E) << 24 | 0xFFFFFF);
        }

        Component label = this.tab.getTitle();
        int textY = this.getCenterY() - this.font.lineHeight / 2;
        this.drawString(guiGraphics, label, this.getX() + TEXT_X, textY, this.textColor());

        if (this.shouldRenderFocusBorder()) {
            this.drawBorder(guiGraphics, this.getX(), this.getY(), this.getLimitX(), this.getLimitY(), GuiThemes.OPTION_FOCUS_BORDER);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
            this.doAction();
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.isFocused() && isSelectionKey(keyCode)) {
            this.doAction();
            return true;
        }

        return false;
    }

    @Override
    public void updateNarration(NarrationElementOutput builder) {
        builder.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.tab", this.tab.getTitle()));

        if (!this.selected) {
            this.addButtonUsageNarration(builder);
        }
    }

    private void doAction() {
        this.action.run();
        this.playClickSound();
    }

    private int textColor() {
        if (this.selected || !ReeseSodiumOptionsConfig.config().isColorThemes()) {
            return 0xFFFFFFFF;
        }

        return this.hovered ? this.theme.themeLighter : GlassUi.SECONDARY_TEXT;
    }
}
