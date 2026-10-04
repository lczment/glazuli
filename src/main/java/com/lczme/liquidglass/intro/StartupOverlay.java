package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.cave.CaveBackground;
import com.lczme.liquidglass.dev.DevHooks;
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
 * over seamlessly from the early loading window. When loading is done the letters turn into windows onto the cave and
 * the camera flies through the logo while the cave camera flies into the title screen behind it.
 */
public final class StartupOverlay extends GlassLoadingOverlay {
    private static final float HANDOFF_SECONDS = 0.5F;
    // Fast loads (small modpacks, dev) would otherwise skip straight to the transition.
    private static final float MIN_LOADING_SECONDS = 1.5F;
    private static final float MAX_FILL_SECONDS = 0.6F;
    /** The letters open onto the cave, so give its mesh a moment; never hold the startup longer than this. */
    private static final float MAX_BACKDROP_WAIT_SECONDS = 2.0F;

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
        if (isFinishCalled() && !splash.outro.isStarted()) {
            CaveBackground.prepare();
        }
        double sinceFill = AnimClock.now() - fillStart;
        boolean filled = progress >= 1.0F || sinceFill >= MAX_FILL_SECONDS;
        boolean backdropReady = CaveBackground.isReady() || sinceFill >= MAX_BACKDROP_WAIT_SECONDS;
        if (isFinishCalled() && !splash.outro.isStarted() && filled && backdropReady) {
            splash.outro.start();
            IntroState.setPhase(IntroState.Phase.OUTRO);
        }
        if (IntroState.consumeSkip() && isFinishCalled()) {
            if (!splash.outro.isStarted()) {
                splash.outro.start();
                IntroState.setPhase(IntroState.Phase.OUTRO);
            }
            splash.outro.skipToEnd();
        }

        drawFrame(guiGraphics, mouseX, mouseY, partialTick, progress);
        DevHooks.onBootFrame(IntroState.phase(), splash.outro.isStarted() ? splash.outro.elapsed() : elapsed());

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

        if (!outroActive && early != null) {
            float handoff = Mth.clamp(elapsed() / HANDOFF_SECONDS, 0.0F, 1.0F);
            if (handoff < 1.0F) {
                early.draw(pose, w, h, 1.0F - Easing.IN_OUT_CUBIC.apply(handoff));
            }
        }
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
