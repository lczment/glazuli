package com.lczme.glazuli.compat.sodium.gui.frame.tab;

import com.lczme.glazuli.anim.AnimClock;
import com.lczme.glazuli.anim.Easing;
import com.lczme.glazuli.compat.sodium.gui.layout.LayoutBounds;
import com.lczme.glazuli.compat.sodium.gui.frame.AbstractFrame;
import com.lczme.glazuli.compat.sodium.gui.frame.option.OptionRow;
import com.lczme.glazuli.compat.sodium.gui.state.Holder;
import com.lczme.glazuli.compat.sodium.gui.state.OptionStateStore;
import com.lczme.glazuli.compat.sodium.gui.widget.BaseWidget;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class TabFrame extends AbstractFrame {

    public static final int TAB_HEIGHT = 18;
    public static final int TAB_HEADER_HEIGHT = 18;
    public static final int TAB_HEADER_VERSION_HEIGHT = 22;
    public static final int TAB_HEADER_PADDING = 4;
    public static final int TAB_RAIL_MIN_WIDTH = 120;
    public static final int TAB_RAIL_MAX_WIDTH = 220;
    public static final int TAB_RAIL_PADDING = 24;
    public static final int TAB_RAIL_SCROLL_PADDING = 32;
    public static final double TAB_RAIL_MAX_WIDTH_RATIO = 0.35D;
    public static final int CARD_GAP = 6;
    public static final int CARD_PADDING = 4;
    private static final float PAGE_SLIDE_SECONDS = 0.32F;
    private static final float PAGE_SLIDE_DISTANCE = 28.0F;

    private final TabRail tabRail;
    private final TabSelectionState tabSelection;
    private final LayoutBounds frameSection;
    private final List<Tab<?>> tabs = new ArrayList<>();
    private final Runnable onSetTab;
    private final Holder<Boolean> scrollSelectedTabIntoView;
    // Page frames share this holder, so inactive-frame rebuilds must not leave their clamped offset in it.
    private final Holder<Integer> optionPageScrollBarOffset;
    private @Nullable Tab<?> shownTab;
    private double slideStart = Double.NaN;
    private int slideDirection;

    public TabFrame(LayoutBounds dim, Screen screen, ModOptions modOptions, boolean renderOutline, List<Tab<?>> tabs, Runnable onSetTab, Holder<String> tabRailSelectedTab, Holder<String> tabRailSelectedGroup, Holder<Integer> tabRailScrollBarOffset, Holder<Boolean> scrollSelectedTabIntoView, Holder<Integer> optionPageScrollBarOffset, Set<String> manuallyCollapsedTabGroups, OptionStateStore optionStateStore) {
        super(dim, screen, renderOutline, modOptions);
        this.tabs.addAll(tabs);
        this.scrollSelectedTabIntoView = scrollSelectedTabIntoView;
        this.optionPageScrollBarOffset = optionPageScrollBarOffset;
        TabGroupModel tabGroupModel = new TabGroupModel(this.tabs, tabRailSelectedGroup, manuallyCollapsedTabGroups);
        LayoutBounds frameDim = this.getFrameDim();

        this.tabRail = new TabRail(frameDim, this.tabs, tabGroupModel, tabRailScrollBarOffset, optionStateStore);
        this.tabSelection = new TabSelectionState(this.tabs, tabRailSelectedTab, screen);
        this.frameSection = this.tabRail.createFrameSection(frameDim);

        this.onSetTab = onSetTab;
        this.tabSelection.restorePersistedTab(this.tabRail::showRestoredSelectedTab);

        this.buildFrame();

        // Let's build each frame, future note for anyone: do not move this line.
        int selectedPageScrollOffset = this.optionPageScrollBarOffset.getOrDefault(0);
        this.tabSelection.warmInactiveFrames(this.frameSection);
        this.optionPageScrollBarOffset.set(selectedPageScrollOffset);
    }

    public static Builder createBuilder() {
        return new Builder();
    }

    public void setTab(Optional<Tab<?>> tab) {
        this.tabSelection.setSelectedTab(tab);
        this.tabRail.selectGroupFor(tab);
        if (this.onSetTab != null) {
            this.onSetTab.run();
        }
        this.tabSelection.activateSelectedTab();
        this.buildFrame();
    }

    public void refreshFromState() {
        int selectedPageScrollOffset = this.optionPageScrollBarOffset.getOrDefault(0);
        this.tabSelection.restorePersistedTab(this.tabRail::showRestoredSelectedTab);
        this.tabRail.selectGroupFor(this.tabSelection.selectedTab());
        this.tabSelection.activateSelectedTab();
        this.tabSelection.refreshFrames(this.frameSection);
        this.optionPageScrollBarOffset.set(selectedPageScrollOffset);
        this.buildFrame();
    }

    @Override
    public void buildFrame() {
        this.children.clear();
        this.optionRows.clear();

        this.tabSelection.ensureSelectedTab(this.tabRail::showRestoredSelectedTab);

        LayoutBounds frameDim = this.getFrameDim();
        this.tabRail.updateScrollState(frameDim);
        this.tabRail.rebuildTabs(this.children, this.tabSelection.selectedTab(), tab -> this.setTab(Optional.of(tab)), this::buildFrame);
        this.tabSelection.rebuildSelectedFrame(this.frameSection, this.children);
        this.tabRail.addScrollBar(this.children);

        if (this.scrollSelectedTabIntoView.getOrDefault(false)) {
            this.scrollSelectedTabIntoView.set(false);
            this.tabRail.scrollSelectedTabIntoView(frameDim);
        }

        super.buildFrame();
        this.registerFocusListener(element -> this.tabRail.scrollFocusedWidgetIntoView(element, frameDim));
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        LayoutBounds frameDim = this.getFrameDim();
        AbstractFrame selectedFrame = this.tabSelection.selectedFrame();
        BaseWidget scrollBar = this.tabRail.scrollBar();
        this.applyScissor(guiGraphics, frameDim.x(), frameDim.y(), frameDim.width(), frameDim.height(), () -> {
            this.tabRail.renderSelectionPill(guiGraphics);
            for (GuiEventListener child : this.children) {
                if (child != selectedFrame && child != scrollBar && child instanceof Renderable renderable) {
                    renderable.render(guiGraphics, mouseX, mouseY, delta);
                }
            }
        });
        if (selectedFrame != null) {
            // A newly selected page slides in from the side of the rail it was picked towards; its own clip stays put.
            float offset = this.pageSlideOffset();
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, offset, 0.0F);
            selectedFrame.render(guiGraphics, mouseX, Math.round(mouseY - offset), delta);
            guiGraphics.pose().popPose();
        }
        this.tabRail.extractScrollBar(guiGraphics, mouseX, mouseY, delta);
    }

    private float pageSlideOffset() {
        Tab<?> tab = this.tabSelection.selectedTab().orElse(null);
        double now = AnimClock.now();
        if (tab != this.shownTab) {
            if (this.shownTab != null && tab != null) {
                this.slideDirection = Integer.signum(this.tabs.indexOf(tab) - this.tabs.indexOf(this.shownTab));
                this.slideStart = now;
            }
            this.shownTab = tab;
        }
        if (Double.isNaN(this.slideStart)) {
            return 0.0F;
        }
        float t = Mth.clamp((float) ((now - this.slideStart) / PAGE_SLIDE_SECONDS), 0.0F, 1.0F);
        if (t >= 1.0F) {
            this.slideStart = Double.NaN;
        }
        return this.slideDirection * PAGE_SLIDE_DISTANCE * (1.0F - Easing.SPEC_EASE.apply(t));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return (this.getFrameDim().contains(mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button)) || this.tabRail.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) || this.tabRail.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button) || this.tabRail.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) || this.tabRail.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    public List<Tab<?>> getTabs() {
        return tabs;
    }

    public AbstractFrame getSelectedFrame() {
        return this.tabSelection.selectedFrame();
    }

    public Optional<Tab<?>> getSelectedTab() {
        return this.tabSelection.selectedTab();
    }

    public Optional<String> getSelectedTabKey() {
        return this.tabSelection.selectedTabKey();
    }

    public OptionRow findSelectedOptionRow(ResourceLocation optionId) {
        return this.tabSelection.findSelectedOptionRow(optionId);
    }

    public OptionRow findFirstSelectedOptionRow() {
        return this.tabSelection.findFirstSelectedOptionRow();
    }

    public OptionRow findLastSelectedOptionRow() {
        return this.tabSelection.findLastSelectedOptionRow();
    }

    public OptionRow findFirstVisibleSelectedOptionRow() {
        return this.tabSelection.findFirstVisibleSelectedOptionRow();
    }

    public OptionRow findLastVisibleSelectedOptionRow() {
        return this.tabSelection.findLastVisibleSelectedOptionRow();
    }

    public boolean scrollSelectedPageToStart() {
        return this.tabSelection.scrollSelectedPageToStart();
    }

    public boolean scrollSelectedPageToEnd() {
        return this.tabSelection.scrollSelectedPageToEnd();
    }

    public boolean scrollSelectedPage(int direction) {
        return this.tabSelection.scrollSelectedPage(direction);
    }

    public static class Builder {
        private final List<Tab<?>> functions = new ArrayList<>();
        private LayoutBounds dim;
        private boolean renderOutline;
        private Runnable onSetTab;
        private Holder<String> tabRailSelectedTab = new Holder<>(null);
        private Holder<String> tabRailSelectedGroup = new Holder<>(null);
        private Holder<Integer> tabRailScrollBarOffset = new Holder<>(0);
        private Holder<Boolean> scrollSelectedTabIntoView = new Holder<>(false);
        private Holder<Integer> optionPageScrollBarOffset = new Holder<>(0);
        private Set<String> manuallyCollapsedTabGroups = new HashSet<>();
        private OptionStateStore optionStateStore;
        private Screen screen;
        private ModOptions modOptions;

        public Builder setDimension(LayoutBounds dim) {
            this.dim = dim;
            return this;
        }

        public Builder shouldRenderOutline(boolean renderOutline) {
            this.renderOutline = renderOutline;
            return this;
        }

        public Builder addTabs(Consumer<List<Tab<?>>> tabs) {
            tabs.accept(this.functions);
            return this;
        }

        public Builder onSetTab(Runnable onSetTab) {
            this.onSetTab = onSetTab;
            return this;
        }

        public Builder setTabRailSelectedTab(Holder<String> tabRailSelectedTab) {
            this.tabRailSelectedTab = tabRailSelectedTab;
            return this;
        }

        public Builder setTabRailSelectedGroup(Holder<String> tabRailSelectedGroup) {
            this.tabRailSelectedGroup = tabRailSelectedGroup;
            return this;
        }

        public Builder setTabRailScrollBarOffset(Holder<Integer> tabRailScrollBarOffset) {
            this.tabRailScrollBarOffset = tabRailScrollBarOffset;
            return this;
        }

        public Builder setScrollSelectedTabIntoView(Holder<Boolean> scrollSelectedTabIntoView) {
            this.scrollSelectedTabIntoView = scrollSelectedTabIntoView;
            return this;
        }

        public Builder setOptionPageScrollBarOffset(Holder<Integer> optionPageScrollBarOffset) {
            this.optionPageScrollBarOffset = optionPageScrollBarOffset;
            return this;
        }

        public Builder setManuallyCollapsedTabGroups(Set<String> manuallyCollapsedTabGroups) {
            this.manuallyCollapsedTabGroups = manuallyCollapsedTabGroups;
            return this;
        }

        public Builder setOptionStateStore(OptionStateStore optionStateStore) {
            this.optionStateStore = optionStateStore;
            return this;
        }

        public Builder withScreen(Screen screen) {
            this.screen = screen;
            return this;
        }

        public Builder withModOptions(ModOptions modOptions) {
            this.modOptions = modOptions;
            return this;
        }

        public TabFrame build() {
            Validate.notNull(this.dim, "Dimension must be specified");
            Validate.notNull(this.optionStateStore, "Option state store must be specified");

            return new TabFrame(this.dim, this.screen, this.modOptions, this.renderOutline, this.functions, this.onSetTab, this.tabRailSelectedTab, this.tabRailSelectedGroup, this.tabRailScrollBarOffset, this.scrollSelectedTabIntoView, this.optionPageScrollBarOffset, this.manuallyCollapsedTabGroups, this.optionStateStore);
        }
    }
}
