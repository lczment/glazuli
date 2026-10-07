package com.lczme.glazuli.compat.sodium.gui.frame.option;

import com.lczme.glazuli.compat.sodium.gui.layout.LayoutBounds;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

import java.util.List;

public interface OptionRow extends Renderable, GuiEventListener, NarratableEntry {
    Option getOption();

    LayoutBounds getDimensions();

    List<NarratableEntry> collectNarratables();

    void releaseActionButtonLayoutHold();

    boolean handleBackNavigation();

    boolean undoFocusedActionButton();

    void clearActionButtonFocus();
}
