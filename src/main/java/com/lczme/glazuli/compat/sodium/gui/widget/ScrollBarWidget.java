package com.lczme.glazuli.compat.sodium.gui.widget;

import com.lczme.glazuli.compat.sodium.gui.layout.LayoutBounds;
import com.lczme.glazuli.render.GlassUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class ScrollBarWidget extends BaseWidget {

    public static final int SCROLL_STEP = 6;
    private static final int THUMB_THICKNESS = 4;

    private final ScrollDirection mode;
    private final int contentLength;
    private final int visibleAreaLength;
    private final int maxContentOffset;
    private final Consumer<Integer> offsetChangeListener;
    private final LayoutBounds extraScrollArea;
    private int offset = 0;
    private boolean isDragging;
    private LayoutBounds scrollThumb = null;
    private int scrollThumbClickOffset;

    public ScrollBarWidget(LayoutBounds trackArea, ScrollDirection scrollDirection, int contentLength, int visibleAreaLength, Consumer<Integer> offsetChangeListener) {
        this(trackArea, scrollDirection, contentLength, visibleAreaLength, offsetChangeListener, null);
    }

    public ScrollBarWidget(LayoutBounds scrollBarArea, ScrollDirection scrollDirection, int contentLength, int visibleAreaLength, Consumer<Integer> offsetChangeListener, LayoutBounds extraScrollArea) {
        super(scrollBarArea);
        this.mode = scrollDirection;
        this.contentLength = contentLength;
        this.visibleAreaLength = visibleAreaLength;
        this.offsetChangeListener = offsetChangeListener;
        this.maxContentOffset = this.contentLength - this.visibleAreaLength;
        this.extraScrollArea = extraScrollArea;
        this.updateThumbLocation();
    }

    public void updateThumbLocation() {
        int trackSize = (this.mode == ScrollDirection.VERTICAL ? this.getHeight() : this.getWidth() - 6);
        int scrollThumbLength = (this.visibleAreaLength * trackSize) / this.contentLength;
        int maximumScrollThumbOffset = this.visibleAreaLength - scrollThumbLength;
        int scrollThumbOffset = (this.offset * maximumScrollThumbOffset) / this.maxContentOffset;
        this.scrollThumb = new LayoutBounds(
                this.getX() + 2 + (this.mode == ScrollDirection.HORIZONTAL ? scrollThumbOffset : 0),
                this.getY() + 2 + (this.mode == ScrollDirection.VERTICAL ? scrollThumbOffset : 0),
                (this.mode == ScrollDirection.VERTICAL ? this.getWidth() : scrollThumbLength) - 4,
                (this.mode == ScrollDirection.VERTICAL ? scrollThumbLength : this.getHeight()) - 4
        );
    }


    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.hovered = this.isMouseOver(mouseX, mouseY);
        float hover = this.hoverAmount(this.hovered || this.isDragging);
        LayoutBounds thumb = this.scrollThumb;
        boolean vertical = this.mode == ScrollDirection.VERTICAL;
        int inset = vertical ? Math.max(0, (thumb.width() - THUMB_THICKNESS) / 2) : Math.max(0, (thumb.height() - THUMB_THICKNESS) / 2);
        GlassUi.roundedFill(guiGraphics,
                vertical ? thumb.x() + inset : thumb.x(), vertical ? thumb.y() : thumb.y() + inset,
                vertical ? thumb.getLimitX() - inset : thumb.getLimitX(), vertical ? thumb.getLimitY() : thumb.getLimitY() - inset,
                2, FastColor.ARGB32.lerp(hover, GlassUi.SCROLL_THUMB, GlassUi.SCROLL_THUMB_ACTIVE));
        if (this.shouldRenderFocusBorder()) {
            this.drawBorder(guiGraphics, this.getX(), this.getY(), this.getLimitX(), this.getLimitY(), -1);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isMouseOver(mouseX, mouseY)) {
            if (this.scrollThumb.contains(mouseX, mouseY)) {
                this.scrollThumbClickOffset = (int) (this.mode == ScrollDirection.VERTICAL ? mouseY - this.scrollThumb.getCenterY() : mouseX - this.scrollThumb.getCenterX());
                this.isDragging = true;
            } else {
                int thumbLength = this.mode == ScrollDirection.VERTICAL ? this.scrollThumb.height() : this.scrollThumb.width();
                int trackLength = this.mode == ScrollDirection.VERTICAL ? this.getHeight() : this.getWidth();
                int value = (int) (((this.mode == ScrollDirection.VERTICAL ? mouseY - this.getY() : mouseX - this.getX()) - thumbLength / 2.0) * this.maxContentOffset / (trackLength - thumbLength));
                this.setOffset(value);
                this.isDragging = false;
            }
            return true;
        }
        this.isDragging = false;
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDragging = false;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDragging) {
            int thumbLength = this.mode == ScrollDirection.VERTICAL ? this.scrollThumb.height() : this.scrollThumb.width();
            int trackLength = this.mode == ScrollDirection.VERTICAL ? this.getHeight() : this.getWidth();
            int value = (int) (((this.mode == ScrollDirection.VERTICAL ? mouseY : mouseX) - this.scrollThumbClickOffset - (this.mode == ScrollDirection.VERTICAL ? this.getY() : this.getX()) - thumbLength / 2.0) * this.maxContentOffset / (trackLength - thumbLength));
            this.setOffset(value);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.isMouseOver(mouseX, mouseY) || this.extraScrollArea != null && this.extraScrollArea.contains(mouseX, mouseY)) {
            this.setOffset(this.offset - (int) verticalAmount * SCROLL_STEP);
            return true;
        }
        return false;
    }

    public int getOffset() {
        return this.offset;
    }

    public void setOffset(int value) {
        this.offset = Mth.clamp(value, 0, this.maxContentOffset);
        this.updateThumbLocation();
        this.offsetChangeListener.accept(this.offset);
    }

    @Override
    public @NotNull ScreenRectangle getRectangle() {
        return new ScreenRectangle(this.getX(), this.getY(), this.getWidth(), this.getHeight());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.isFocused()) {
            return false;
        }

        int newOffset = switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> this.getOffset() - SCROLL_STEP;
            case GLFW.GLFW_KEY_DOWN -> this.getOffset() + SCROLL_STEP;
            case GLFW.GLFW_KEY_LEFT ->
                    this.mode == ScrollDirection.HORIZONTAL ? this.getOffset() - SCROLL_STEP : this.getOffset();
            case GLFW.GLFW_KEY_RIGHT ->
                    this.mode == ScrollDirection.HORIZONTAL ? this.getOffset() + SCROLL_STEP : this.getOffset();
            default -> this.getOffset();
        };

        if (newOffset != this.getOffset()) {
            this.setOffset(newOffset);
            return true;
        }

        return false;
    }

    @Override
    public boolean isActive() {
        return this.maxContentOffset > 0;
    }

    @Override
    public void updateNarration(NarrationElementOutput builder) {
        Component name = Component.translatable(this.mode == ScrollDirection.VERTICAL
                ? "rso.narration.scrollbar.vertical"
                : "rso.narration.scrollbar.horizontal");
        int percentage = this.maxContentOffset <= 0 ? 0 : Math.round(this.offset * 100.0F / this.maxContentOffset);

        builder.add(NarratedElementType.TITLE, CommonComponents.optionNameValue(name, Component.literal(percentage + "%")));
        if (this.isFocused()) {
            builder.add(NarratedElementType.USAGE, Component.translatable(this.mode == ScrollDirection.VERTICAL
                    ? "rso.narration.scrollbar.usage.vertical"
                    : "rso.narration.scrollbar.usage.horizontal"));
        }
    }

    public enum ScrollDirection {
        HORIZONTAL,
        VERTICAL
    }
}
