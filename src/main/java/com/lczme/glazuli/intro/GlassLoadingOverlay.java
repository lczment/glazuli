package com.lczme.glazuli.intro;

import com.lczme.glazuli.anim.AnimClock;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;

/**
 * Shared base of our loading overlays. Extends {@link LoadingOverlay} (instead of {@code Overlay}) because vanilla and
 * other mods test {@code instanceof LoadingOverlay}, e.g. to queue resource reloads requested while one is running.
 * The parent's own rendering and state are never used.
 */
public abstract class GlassLoadingOverlay extends LoadingOverlay {
    protected final Minecraft minecraft;
    protected final ReloadInstance reload;
    private final Consumer<Optional<Throwable>> onFinish;
    protected final boolean fadeIn;
    protected final LoadingBar bar = new LoadingBar();
    private double startTime = Double.NaN;
    private boolean finishCalled;

    protected GlassLoadingOverlay(Minecraft minecraft, ReloadInstance reload, Consumer<Optional<Throwable>> onFinish, boolean fadeIn) {
        super(minecraft, reload, onFinish, fadeIn);
        this.minecraft = minecraft;
        this.reload = reload;
        this.onFinish = onFinish;
        this.fadeIn = fadeIn;
    }

    /** Seconds since this overlay was first rendered. */
    public float elapsed() {
        return Double.isNaN(startTime) ? 0.0F : (float) (AnimClock.now() - startTime);
    }

    protected final void markFrame() {
        if (Double.isNaN(startTime)) {
            startTime = AnimClock.now();
        }
    }

    /** The reload result has been applied, so resources are consistent again even though the overlay is still up. */
    public final boolean isFinishCalled() {
        return finishCalled;
    }

    /** Hands the reload result to the owner exactly like vanilla, including re-initializing the current screen. */
    protected final void finishReload(GuiGraphics guiGraphics) {
        finishCalled = true;
        try {
            reload.checkExceptions();
            onFinish.accept(Optional.empty());
        } catch (Throwable throwable) {
            onFinish.accept(Optional.of(throwable));
        }
        if (minecraft.screen != null) {
            minecraft.screen.init(minecraft, guiGraphics.guiWidth(), guiGraphics.guiHeight());
        }
    }

    /** Whether the completion callback replaced us with another overlay (e.g. resource pack rollback after an error). */
    protected final boolean isReplaced() {
        return minecraft.getOverlay() != this;
    }

    protected final void remove() {
        if (minecraft.getOverlay() == this) {
            minecraft.setOverlay(null);
        }
    }
}
