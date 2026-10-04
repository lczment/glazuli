package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.anim.Timeline;
import com.lczme.liquidglass.dev.DevHooks;
import com.lczme.liquidglass.render.GlassRenderer;
import com.lczme.liquidglass.render.GlassStyle;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;

/**
 * Branded overlay for the initial resource load: dissolves out of the early loading window, shows the logo and
 * progress, then "defrosts" into the first screen.
 * <p>
 * Until loading has finished only {@link IntroDraw}, {@link IntroText}, {@link IntroLogo} and {@link IntroBackdrop}
 * are used, none of which depend on anything the reload is still building. The outro runs after the reload, so it
 * can use {@link GlassRenderer}.
 */
public final class StartupOverlay extends GlassLoadingOverlay {
    private static final float HANDOFF_SECONDS = 0.6F;
    private static final float BLACK_FADE_SECONDS = 0.35F;
    private static final float LOGO_DELAY = 0.15F;
    /** Start without the logo texture if it takes this long (it then pops in when ready). */
    private static final float LOGO_WAIT_LIMIT = 1.2F;
    // Fast loads (small modpacks, dev) would otherwise flash the intro by before it can be seen.
    private static final float MIN_LOGO_SECONDS = 2.0F;
    private static final float MAX_FILL_SECONDS = 0.6F;
    private static final float SHEEN_START = 1.0F;
    private static final float SHEEN_PERIOD = 3.4F;
    private static final float SHEEN_SWEEP = 1.1F;
    private static final String DEFAULT_STAGE = "Loading resources";
    private static final String READY_STAGE = "Ready";
    private static final GlassStyle FROST = GlassStyle.film(0x0B0D12, GlassStyle.TINT_NEUTRAL).withCornerRadius(0.0F).withShadow(0.0F);

    @Nullable
    private final EarlyWindowHandoff early;
    @Nullable
    private final ProgressMeter meter;

    private final Timeline intro = new Timeline();
    private final Timeline.Track logoAlpha = intro.track(0.0F).to(0.0F, 0.7F, 1.0F, Easing.OUT_CUBIC);
    private final Timeline.Track logoScale = intro.track(0.9F).to(0.0F, 0.9F, 1.0F, Easing.OUT_BACK);
    private final Timeline.Track logoRise = intro.track(1.0F).to(0.0F, 0.9F, 0.0F, Easing.OUT_EXPO);
    private final Timeline.Track barAlpha = intro.track(0.0F).to(0.35F, 0.5F, 1.0F, Easing.OUT_CUBIC);

    private final Timeline outro = new Timeline();
    private final Timeline.Track outroBar = outro.track(1.0F).to(0.0F, 0.3F, 0.0F, Easing.OUT_CUBIC);
    private final Timeline.Track outroBackdrop = outro.track(1.0F).to(0.05F, 0.7F, 0.0F, Easing.IN_OUT_CUBIC);
    private final Timeline.Track outroFrost = outro.track(1.0F).to(0.25F, 0.85F, 0.0F, Easing.IN_OUT_CUBIC);
    private final Timeline.Track outroLogoMove = outro.track(0.0F).to(0.0F, 0.9F, 1.0F, Easing.IN_OUT_CUBIC);
    private final Timeline.Track outroLogoAlpha = outro.track(1.0F).to(0.2F, 0.6F, 0.0F, Easing.IN_CUBIC);

    private double fillStart = Double.NaN;
    private boolean ended;

    StartupOverlay(Minecraft minecraft, ReloadInstance reload, Consumer<Optional<Throwable>> onFinish, boolean fadeIn,
                   @Nullable EarlyWindowHandoff early, @Nullable ProgressMeter meter) {
        super(minecraft, reload, onFinish, fadeIn);
        this.early = early;
        this.meter = meter;
        IntroState.setOutroDuration(outro.length());
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
        bar.setLabel(isFinishCalled() ? READY_STAGE : stageLabel());

        float logoHeight = logoHeight(guiGraphics.guiHeight());
        IntroLogo.prepare(logoHeight);
        float t = elapsed();
        if (!intro.isStarted() && (IntroLogo.isReady() && t >= LOGO_DELAY || t >= LOGO_WAIT_LIMIT)) {
            intro.start();
        }

        if (!isFinishCalled() && reload.isDone() && intro.isStarted() && intro.elapsed() >= MIN_LOGO_SECONDS) {
            finishReload(guiGraphics);
            IntroState.setLoadingDone();
            fillStart = AnimClock.now();
            if (isReplaced()) {
                end();
                return;
            }
        }
        if (isFinishCalled() && !outro.isStarted() && (progress >= 1.0F || AnimClock.now() - fillStart >= MAX_FILL_SECONDS)) {
            startOutro();
        }
        if (IntroState.consumeSkip() && isFinishCalled()) {
            if (!outro.isStarted()) {
                startOutro();
            }
            outro.skipToEnd();
        }

        drawFrame(guiGraphics, mouseX, mouseY, partialTick, logoHeight);
        DevHooks.onBootFrame(IntroState.phase(), outro.isStarted() ? outro.elapsed() : elapsed());

        if (outro.isFinished()) {
            end();
        }
    }

    private void startOutro() {
        outro.start();
        IntroState.setPhase(IntroState.Phase.OUTRO);
    }

    private void drawFrame(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, float logoHeight) {
        float w = guiGraphics.guiWidth();
        float h = guiGraphics.guiHeight();
        boolean outroActive = outro.isStarted();
        float backdropAlpha;
        if (outroActive) {
            if (minecraft.screen != null) {
                ClientHooks.drawScreen(minecraft.screen, guiGraphics, mouseX, mouseY, partialTick);
            }
            guiGraphics.flush();
            float frost = outroFrost.value();
            if (frost > 0.0F) {
                GlassRenderer.recaptureBackdrop(guiGraphics);
                GlassRenderer.drawPanel(guiGraphics, -16.0F, -16.0F, w + 32.0F, h + 32.0F, FROST.withOpacity(frost));
            }
            backdropAlpha = outroBackdrop.value();
        } else {
            float t = elapsed();
            Matrix4f pose = guiGraphics.pose().last().pose();
            float handoff;
            if (early != null) {
                handoff = Mth.clamp(t / HANDOFF_SECONDS, 0.0F, 1.0F);
                if (handoff < 1.0F) {
                    early.draw(pose, w, h);
                }
            } else {
                handoff = Mth.clamp(t / BLACK_FADE_SECONDS, 0.0F, 1.0F);
                if (handoff < 1.0F) {
                    IntroDraw.begin();
                    IntroDraw.fill(pose, 0.0F, 0.0F, w, h, 0xFF000000);
                    IntroDraw.end();
                }
            }
            backdropAlpha = Easing.IN_OUT_CUBIC.apply(handoff);
        }
        Matrix4f pose = guiGraphics.pose().last().pose();

        float restY = h * 0.5F - logoHeight * 0.45F;
        float scale = logoScale.value();
        float cy = restY + 8.0F * logoRise.value();
        float alpha = logoAlpha.value();
        float sheen = 2.0F;
        if (outroActive) {
            float m = outroLogoMove.value();
            cy = Mth.lerp(m, cy, IntroState.menuLogoCenterY(h));
            scale *= Mth.lerp(m, 1.0F, IntroState.MENU_LOGO_SCALE);
            if (!IntroState.isLogoHandoffRequested()) {
                alpha *= outroLogoAlpha.value();
            }
        } else if (intro.elapsed() >= SHEEN_START) {
            float cycle = ((intro.elapsed() - SHEEN_START) % SHEEN_PERIOD) / SHEEN_SWEEP;
            sheen = cycle * 1.6F - 0.3F;
        }
        float drawnHeight = logoHeight * scale;
        float cx = w * 0.5F;
        IntroState.setLogoPose(new IntroState.LogoPose(cx, cy, drawnHeight * IntroLogo.aspect(), drawnHeight, alpha));

        IntroBackdrop.draw(pose, w, h, backdropAlpha, cx, cy, logoHeight * IntroLogo.aspect() * 0.55F,
                0.12F * alpha * (outroActive ? backdropAlpha : 1.0F));
        IntroLogo.draw(pose, cx, cy, drawnHeight, alpha, sheen);

        float barWidth = Mth.clamp(w * 0.25F, 90.0F, 220.0F);
        float barY = restY + logoHeight * 0.5F + 18.0F;
        bar.draw(pose, cx, barY, barWidth, barAlpha.value() * (outroActive ? outroBar.value() : 1.0F));
    }

    private void end() {
        ended = true;
        IntroState.setPhase(IntroState.Phase.FINISHED);
        if (meter != null) {
            meter.complete();
        }
        if (early != null) {
            early.close();
        }
        IntroBackdrop.close();
        IntroText.clear();
        IntroOverlays.onIntroPlayed(minecraft);
        remove();
    }

    static float logoHeight(float screenHeight) {
        return Mth.clamp(screenHeight * 0.12F, 20.0F, 44.0F);
    }

    private String stageLabel() {
        try {
            List<ProgressMeter> meters = StartupNotificationManager.getCurrentProgress();
            for (int i = meters.size() - 1; i >= 0; i--) {
                ProgressMeter m = meters.get(i);
                if (m != meter && m.name() != null && !m.name().isBlank()) {
                    return sanitize(m.name());
                }
            }
        } catch (RuntimeException ignored) {
            // Progress tracking is purely cosmetic here.
        }
        return DEFAULT_STAGE;
    }

    private static String sanitize(String text) {
        String clean = text.replaceAll("[^\\x20-\\x7E]", "").trim();
        return clean.length() > 48 ? clean.substring(0, 47) + "\u2026" : clean;
    }
}
