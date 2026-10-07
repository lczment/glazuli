package com.lczme.glazuli.intro;

import com.lczme.glazuli.anim.AnimClock;
import com.lczme.glazuli.cave.CaveBackground;
import com.lczme.glazuli.dev.DevHooks;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;

/**
 * LabyMod-style startup: the familiar Mojang Studios loading screen (brand colour, logo, vanilla progress bar) takes
 * over from the early loading window with a hard cut (background and logo line up, so only FML's progress bars and log
 * lines disappear; dissolving them made them flash). When loading is done the letters turn into windows onto the cave and
 * the camera flies through the logo while the cave camera flies into the title screen behind it.
 */
public final class StartupOverlay extends GlassLoadingOverlay {
    // Fast loads (small modpacks, dev) would otherwise skip straight to the transition.
    private static final float MIN_LOADING_SECONDS = 1.5F;
    private static final float MAX_FILL_SECONDS = 0.6F;

    @Nullable
    private final EarlyWindowHandoff early;
    @Nullable
    private final ProgressMeter meter;
    private final MojangSplash splash;

    private double fillStart = Double.NaN;
    private boolean ended;

    StartupOverlay(Minecraft minecraft, ReloadInstance reload, Consumer<Optional<Throwable>> onFinish, boolean fadeIn,
                   @Nullable EarlyWindowHandoff early, @Nullable ProgressMeter meter) {
        super(minecraft, reload, onFinish, fadeIn);
        this.early = early;
        this.meter = meter;
        this.splash = new MojangSplash(minecraft);
        splash.animation.load();
        IntroState.setOutroDuration(splash.outro.length());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (ended) {
            return;
        }
        markFrame();
        float progress = bar.update(reload.getActualProgress(), reload.isDone());
        if (meter != null) {
            meter.setAbsolute(Mth.ceil(progress * meter.steps()));
        }

        if (!isFinishCalled() && reload.isDone() && elapsed() >= MIN_LOADING_SECONDS) {
            finishReload(guiGraphics);
            IntroState.setLoadingDone();
            fillStart = AnimClock.now();
            if (isReplaced()) {
                end();
                return;
            }
        }
        splash.animation.tick();
        if (isFinishCalled() && !splash.outro.isStarted()) {
            // Paused while the animation plays (the cave stays warm), resumed just before the outro shows it.
            CaveBackground.prepare(!splash.animation.isStarted() || splash.animation.remaining() < 0.3);
        }
        double sinceFill = AnimClock.now() - fillStart;
        boolean filled = progress >= 1.0F || sinceFill >= MAX_FILL_SECONDS;
        // The shader cave's warm-up stalls frames: the first frame holds until it's done, then the animation plays smoothly.
        boolean backdropReady = CaveBackground.isReady() || sinceFill >= CaveBackground.readyWaitLimitSeconds();
        if (isFinishCalled() && filled && backdropReady) {
            splash.animation.start();
        }
        if (isFinishCalled() && !splash.outro.isStarted() && filled && backdropReady && splash.animation.isFinished()) {
            splash.outro.start();
            IntroState.setPhase(IntroState.Phase.OUTRO);
        }
        if (IntroState.consumeSkip() && isFinishCalled()) {
            splash.animation.skip();
            if (!splash.outro.isStarted()) {
                splash.outro.start();
                IntroState.setPhase(IntroState.Phase.OUTRO);
            }
            splash.outro.skipToEnd();
        }

        drawFrame(guiGraphics, mouseX, mouseY, partialTick, progress);
        DevHooks.onBootFrame(IntroState.phase(), splash.outro.isStarted() ? splash.outro.elapsed() : elapsed());
        if (splash.animation.isStarted() && !splash.outro.isStarted()) {
            DevHooks.onLogoAnimationFrame(splash.animation.elapsed());
        }

        if (splash.outro.isFinished()) {
            end();
        }
    }

    private void drawFrame(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, float progress) {
        float w = guiGraphics.guiWidth();
        float h = guiGraphics.guiHeight();
        boolean outroActive = splash.outro.isStarted();
        if (outroActive && minecraft.screen != null) {
            ClientHooks.drawScreen(minecraft.screen, guiGraphics, mouseX, mouseY, partialTick);
        }
        guiGraphics.flush();
        Matrix4f pose = guiGraphics.pose().last().pose();

        IntroDraw.begin();
        if (outroActive) {
            splash.drawOutro(pose, w, h, brandColor(), progress);
        } else {
            splash.drawLoading(pose, w, h, brandColor(), progress, 1.0F);
        }
        IntroDraw.end();
    }

    private int brandColor() {
        return early != null ? early.background() : MojangSplash.brandColor(minecraft);
    }

    private void end() {
        ended = true;
        splash.close();
        IntroState.setPhase(IntroState.Phase.FINISHED);
        if (meter != null) {
            meter.complete();
        }
        if (early != null) {
            early.close();
        }
        IntroOverlays.onIntroPlayed(minecraft);
        remove();
    }

    static float logoHeight(float screenHeight) {
        return Mth.clamp(screenHeight * 0.12F, 20.0F, 44.0F);
    }
}
