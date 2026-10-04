package com.lczme.liquidglass.compat.sodium.gui.frame.option;

import com.lczme.liquidglass.render.GlassUi;
import com.lczme.liquidglass.compat.sodium.gui.layout.LayoutBounds;
import com.lczme.liquidglass.compat.sodium.gui.state.OptionStateStore;
import com.lczme.liquidglass.compat.sodium.gui.theme.GuiTheme;
import net.caffeinemc.mods.sodium.client.config.structure.ExternalButtonOption;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

final class ExternalButtonOptionRow extends AbstractOptionRow {
    private static final int CONTENT_WIDTH = 65;
    private static final Component BASE_BUTTON_TEXT = Component.translatable("sodium.options.open_external_page_button");

    private final Screen screen;
    private final ExternalButtonOption option;

    ExternalButtonOptionRow(Screen screen, LayoutBounds dim, GuiTheme theme, OptionStateStore optionStateStore, ExternalButtonOption option) {
        super(dim, theme, optionStateStore, option);
        this.screen = screen;
        this.option = option;
    }

    @Override
    public ExternalButtonOption getOption() {
        return this.option;
    }

    @Override
    protected int controlContentWidth() {
        return CONTENT_WIDTH;
    }

    @Override
    protected void renderControl(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        Component text = this.buttonText();
        int x = this.rightAlignedControlX(this.font.width(text));
        int y = this.centeredTextY();

        this.drawString(guiGraphics, text, x, y, GlassUi.TEXT);

        if (this.option.isEnabled()) {
            this.requestPointerCursorIfHovered(guiGraphics);
        }
    }

    @Override
    protected boolean activateControl() {
        if (!this.option.isEnabled()) {
            return false;
        }

        this.option.getCurrentScreenConsumer().accept(this.screen);
        this.playClickSound();

        return true;
    }

    private Component buttonText() {
        if (!this.option.isEnabled()) {
            return BASE_BUTTON_TEXT.copy().withStyle(ChatFormatting.STRIKETHROUGH, ChatFormatting.DARK_GRAY);
        }

        return Component.empty()
                .append(BASE_BUTTON_TEXT.copy().withStyle(ChatFormatting.UNDERLINE))
                .append(Component.literal(" >").withStyle(Style.EMPTY.withColor(this.theme.theme)));
    }

    @Override
    protected void updateControlNarration(NarrationElementOutput builder) {
        if (!this.option.isEnabled()) {
            builder.add(NarratedElementType.HINT, Component.translatable("rso.narration.option_unavailable"));
            return;
        }

        this.addUsageNarration(builder, "narration.link.usage.focused", "narration.link.usage.hovered");
    }
}
