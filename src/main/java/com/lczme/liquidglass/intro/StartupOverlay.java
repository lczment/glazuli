package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.anim.Timeline;
import com.lczme.liquidglass.dev.DevHooks;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;

/**
 * LabyMod-style startup: the familiar Mojang Studios loading screen (brand colour, logo, vanilla progress bar) takes
 * over seamlessly from the early loading window. When loading is done the brand background turns transparent over the
 * cave, the logo lingers and fades, and the cave camera flies into the title screen behind it.
 * <p>
 * Until loading has finished only {@link IntroDraw} is used (vanilla preloads its shaders) and the Mojang logo texture,
 * which vanilla registers from its own jar before the first reload.
 */
public final class StartupOverlay extends GlassLoadingOverlay {
    private static final ResourceLocation MOJANG_LOGO = ResourceLocation.withDefaultNamespace("textures/gui/title/mojangstudios.png");
    private static final int BRAND_RED = 0xFFEF323D;
    private static final int BRAND_DARK = 0xFF000000;
    private static final float HANDOFF_SECONDS = 0.5F;
    // Fast loads (small modpacks, dev) would otherwise skip straight to the transition.
    private static final float MIN_LOADING_SECONDS = 1.5F;
    private static final float MAX_FILL_SECONDS = 0.6F;

    @Nullable
    private final EarlyWindowHandoff early;
    @Nullable
    private final ProgressMeter meter;

    private final Timeline outro = new Timeline();
    private final Timeline.Track outroBar = outro.track(1.0F).to(0.0F, 0.25F, 0.0F, Easing.OUT_CUBIC);
    private final Timeline.Track outroBackground = outro.track(1.0F).to(0.1F, 0.9F, 0.0F, Easing.IN_OUT_CUBIC);
    private final Timeline.Track outroLogo = outro.track(1.0F).to(0.75F, 0.7F, 0.0F, Easing.IN_OUT_CUBIC);
    private final Timeline.Track outroLogoScale = outro.track(1.0F).to(0.1F, 1.35F, 1.06F, Easing.OUT_CUBIC);

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

        if (!isFinishCalled() && reload.isDone() && elapsed() >= MIN_LOADING_SECONDS) {
            finishReload(guiGraphics);
            IntroState.setLoadingDone();
            fillStart = AnimClock.now();
            if (isReplaced()) {
                end();
                return;
            }
        }
        if (isFinishCalled() && !outro.isStarted() && (progress >= 1.0F || AnimClock.now() - fillStart >= MAX_FILL_SECONDS)) {
            outro.start();
            IntroState.setPhase(IntroState.Phase.OUTRO);
        }
        if (IntroState.consumeSkip() && isFinishCalled()) {
            if (!outro.isStarted()) {
                outro.start();
                IntroState.setPhase(IntroState.Phase.OUTRO);
            }
            outro.skipToEnd();
        }

        drawFrame(guiGraphics, mouseX, mouseY, partialTick, progress);
        DevHooks.onBootFrame(IntroState.phase(), outro.isStarted() ? outro.elapsed() : elapsed());

        if (outro.isFinished()) {
            end();
        }
    }

    private void drawFrame(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, float progress) {
        float w = guiGraphics.guiWidth();
        float h = guiGraphics.guiHeight();
        boolean outroActive = outro.isStarted();
        if (outroActive && minecraft.screen != null) {
            ClientHooks.drawScreen(minecraft.screen, guiGraphics, mouseX, mouseY, partialTick);
        }
        guiGraphics.flush();
        Matrix4f pose = guiGraphics.pose().last().pose();

        IntroDraw.begin();
        float background = outroActive ? outroBackground.value() : 1.0F;
        if (background > 0.0F) {
            IntroDraw.fill(pose, 0.0F, 0.0F, w, h, IntroDraw.withAlpha(brandColor(), background));
        }
        drawMojangLogo(pose, w, h, outroActive ? outroLogo.value() : 1.0F, outroActive ? outroLogoScale.value() : 1.0F);
        drawProgressBar(pose, w, h, progress, outroActive ? outroBar.value() : 1.0F);
        IntroDraw.end();

        if (!outroActive && early != null) {
            float handoff = Mth.clamp(elapsed() / HANDOFF_SECONDS, 0.0F, 1.0F);
            if (handoff < 1.0F) {
                early.draw(pose, w, h, 1.0F - Easing.IN_OUT_CUBIC.apply(handoff));
            }
        }
    }

    private int brandColor() {
        if (early != null) {
            return early.background();
        }
        return minecraft.options.darkMojangStudiosBackground().get() ? BRAND_DARK : BRAND_RED;
    }

    /** Same placement and additive blend as vanilla's {@code LoadingOverlay}; the texture stacks the two word halves. */
    private void drawMojangLogo(Matrix4f pose, float w, float h, float alpha, float scale) {
        if (alpha <= 0.0F) {
            return;
        }
        int textureId = minecraft.getTextureManager().getTexture(MOJANG_LOGO).getId();
        float cx = (int) (w * 0.5F);
        float cy = (int) (h * 0.5F);
        float logoH = (float) (Math.min(w * 0.75, h) * 0.25) * scale;
        float halfW = logoH * 2.0F;
        IntroDraw.blendAdditive();
        IntroDraw.textureRegion(pose, textureId, cx - halfW, cy - logoH * 0.5F, halfW, logoH,
                -0.0625F / 120.0F, 0.0F, 119.9375F / 120.0F, 0.5F, alpha);
        IntroDraw.textureRegion(pose, textureId, cx, cy - logoH * 0.5F, halfW, logoH,
                0.0625F / 120.0F, 0.5F, 120.0625F / 120.0F, 1.0F, alpha);
        IntroDraw.blendNormal();
    }

    /** Vanilla's outlined white bar under the logo. */
    private static void drawProgressBar(Matrix4f pose, float w, float h, float progress, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        float halfW = (float) (Math.min(w * 0.75, h) * 0.25) * 2.0F;
        float cy = (int) (h * 0.8325F);
        float x0 = (int) (w * 0.5F - halfW);
        float x1 = (int) (w * 0.5F + halfW);
        float y0 = cy - 5.0F;
        float y1 = cy + 5.0F;
        int color = IntroDraw.withAlpha(0xFFFFFFFF, alpha);
        float filled = (float) Math.ceil((x1 - x0 - 2.0F) * Mth.clamp(progress, 0.0F, 1.0F));
        IntroDraw.fill(pose, x0 + 2.0F, y0 + 2.0F, x0 + filled, y1 - 2.0F, color);
        IntroDraw.fill(pose, x0 + 1.0F, y0, x1 - 1.0F, y0 + 1.0F, color);
        IntroDraw.fill(pose, x0 + 1.0F, y1 - 1.0F, x1 - 1.0F, y1, color);
        IntroDraw.fill(pose, x0, y0, x0 + 1.0F, y1, color);
        IntroDraw.fill(pose, x1 - 1.0F, y0, x1, y1, color);
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
        IntroOverlays.onIntroPlayed(minecraft);
        remove();
    }

    static float logoHeight(float screenHeight) {
        return Mth.clamp(screenHeight * 0.12F, 20.0F, 44.0F);
    }
}
