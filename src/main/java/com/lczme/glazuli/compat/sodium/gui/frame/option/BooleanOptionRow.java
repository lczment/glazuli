package com.lczme.glazuli.compat.sodium.gui.frame.option;

import com.lczme.glazuli.anim.Spring;
import com.lczme.glazuli.compat.sodium.gui.layout.LayoutBounds;
import com.lczme.glazuli.render.GlassUi;
import org.jetbrains.annotations.Nullable;
import com.lczme.glazuli.compat.sodium.gui.state.OptionStateStore;
import com.lczme.glazuli.compat.sodium.gui.theme.GuiTheme;
import net.caffeinemc.mods.sodium.client.config.structure.BooleanOption;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;


final class BooleanOptionRow extends AbstractOptionRow {
    private static final int CONTENT_WIDTH = 30;
    private static final int SWITCH_WIDTH = 20;
    private static final int SWITCH_HEIGHT = 11;
    private static final float KNOB_RESPONSE = 0.3F;
    private static final float KNOB_DAMPING = 0.72F;

    private final BooleanOption option;
    private @Nullable Spring knob;

    BooleanOptionRow(LayoutBounds dim, GuiTheme theme, OptionStateStore optionStateStore, BooleanOption option) {
        super(dim, theme, optionStateStore, option);
        this.option = option;
    }

    @Override
    public BooleanOption getOption() {
        return this.option;
    }

    @Override
    protected int controlContentWidth() {
        return CONTENT_WIDTH;
    }

    @Override
    protected void renderControl(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if (!this.option.showControl()) {
            return;
        }

        LayoutBounds switchDim = this.switchDim();
        boolean checked = this.option.getValidatedValue();
        if (this.knob == null) {
            this.knob = Spring.of(KNOB_RESPONSE, KNOB_DAMPING, checked ? 1.0F : 0.0F);
        }
        this.knob.setTarget(checked);
        GlassUi.toggle(guiGraphics, switchDim.x(), switchDim.y(), switchDim.width(), switchDim.height(), this.knob.value(),
                this.hovered ? 1.0F : 0.0F, this.option.isEnabled());

        this.requestPointerCursorIfHovered(guiGraphics);
    }

    @Override
    protected boolean activateControl() {
        if (!this.option.isEnabled() || !this.option.showControl()) {
            return false;
        }

        this.option.modifyValue(!this.option.getValidatedValue());
        this.playClickSound();

        return true;
    }

    @Override
    protected Component narrationValue() {
        return this.option.showControl() ? CommonComponents.optionStatus(this.option.getValidatedValue()) : null;
    }

    @Override
    protected void updateControlNarration(NarrationElementOutput builder) {
        if (!this.option.isEnabled()) {
            builder.add(NarratedElementType.HINT, Component.translatable("rso.narration.option_unavailable"));
            return;
        }

        if (!this.option.showControl()) {
            return;
        }

        boolean checked = this.option.getValidatedValue();
        if (this.isFocused()) {
            builder.add(NarratedElementType.USAGE, Component.translatable(checked
                    ? "narration.checkbox.usage.focused.uncheck"
                    : "narration.checkbox.usage.focused.check"));
        } else if (this.isHovered()) {
            builder.add(NarratedElementType.USAGE, Component.translatable(checked
                    ? "narration.checkbox.usage.hovered.uncheck"
                    : "narration.checkbox.usage.hovered.check"));
        }
    }

    private LayoutBounds switchDim() {
        int x = this.rightAlignedControlX(SWITCH_WIDTH);
        int y = this.getDimensions().getCenterY() - SWITCH_HEIGHT / 2;

        return new LayoutBounds(x, y, SWITCH_WIDTH, SWITCH_HEIGHT);
    }
}
