package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.anim.Timeline;
import com.lczme.liquidglass.cave.CaveBackground;
import com.lczme.liquidglass.dev.DevHooks;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

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
    /** The letters open onto the cave, so give its mesh a moment; never hold the startup longer than this. */
    private static final float MAX_BACKDROP_WAIT_SECONDS = 2.0F;

    @Nullable
    private final EarlyWindowHandoff early;
    @Nullable
    private final ProgressMeter meter;

    /** Combined logo is 240 x 60 texture units (two stacked 120 x 60 halves side by side). */
    private static final float LOGO_UNITS_W = 240.0F;
    private static final float LOGO_UNITS_H = 60.0F;
    // Middle of a solid letter stroke closest to the logo's centre: the camera flies into it, so the cut-out letter,
    // not the red between letters, fills the screen.
    private static final float PIVOT_U = 117.5F;
    private static final float PIVOT_V = 26.5F;
    private static final float MAX_ZOOM = 80.0F;

    // Outro: the letters turn into windows onto the cave, then the camera flies through the middle of the logo.
    private final Timeline outro = new Timeline();
    private final Timeline.Track outroBar = outro.track(1.0F).to(0.0F, 0.25F, 0.0F, Easing.OUT_CUBIC);
    private final Timeline.Track outroCut = outro.track(0.0F).to(0.05F, 0.45F, 1.0F, Easing.IN_OUT_CUBIC);
    private final Timeline.Track outroZoom = outro.track(0.0F).to(0.45F, 1.15F, 1.0F, Easing.IN_OUT_CUBIC);
    private final Timeline.Track outroBackground = outro.track(1.0F).to(1.2F, 0.4F, 0.0F, Easing.IN_CUBIC);

    @Nullable
    private TextureTarget mask;
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
        if (isFinishCalled() && !outro.isStarted()) {
            CaveBackground.prepare();
        }
        double sinceFill = AnimClock.now() - fillStart;
        boolean filled = progress >= 1.0F || sinceFill >= MAX_FILL_SECONDS;
        boolean backdropReady = CaveBackground.isReady() || sinceFill >= MAX_BACKDROP_WAIT_SECONDS;
        if (isFinishCalled() && !outro.isStarted() && filled && backdropReady) {
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
        if (outroActive) {
            drawFlythrough(pose, w, h);
        } else {
            IntroDraw.fill(pose, 0.0F, 0.0F, w, h, brandColor());
            IntroDraw.blendAdditive();
            drawMojangLogo(pose, w, h, 1.0F);
            IntroDraw.blendNormal();
        }
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

    /**
     * The brand background is rendered into {@link #mask} with the logo erased from it, so the letters show the cave
     * behind; the result is composited over the screen while the camera zooms into the logo.
     */
    private void drawFlythrough(Matrix4f pose, float w, float h) {
        float cut = outroCut.value();
        float background = outroBackground.value();
        Matrix4f zoomed = zoomPose(pose, w, h, outroZoom.value());
        if (background > 0.0F) {
            RenderTarget main = minecraft.getMainRenderTarget();
            if (mask == null || mask.width != main.width || mask.height != main.height) {
                if (mask != null) {
                    mask.destroyBuffers();
                }
                mask = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
                mask.setFilterMode(GL11.GL_LINEAR);
            }
            int color = brandColor();
            mask.setClearColor(FastColor.ARGB32.red(color) / 255.0F, FastColor.ARGB32.green(color) / 255.0F,
                    FastColor.ARGB32.blue(color) / 255.0F, 1.0F);
            mask.clear(Minecraft.ON_OSX);
            mask.bindWrite(true);
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            drawMojangLogo(zoomed, w, h, cut);
            main.bindWrite(true);

            IntroDraw.blendPremultiplied();
            drawMask(pose, w, h, background);
        }
        IntroDraw.blendAdditive();
        drawMojangLogo(zoomed, w, h, 1.0F - cut);
        IntroDraw.blendNormal();
    }

    /** Perspective fly-in: the pivot stroke drifts to the screen centre while the distance to the logo shrinks. */
    private static Matrix4f zoomPose(Matrix4f pose, float w, float h, float t) {
        if (t <= 0.0F) {
            return pose;
        }
        float scale = 1.0F / (1.0F - t * (1.0F - 1.0F / MAX_ZOOM));
        float logoH = logoDrawHeight(w, h);
        float px = (int) (w * 0.5F) - logoH * 2.0F + PIVOT_U / LOGO_UNITS_W * logoH * 4.0F;
        float py = (int) (h * 0.5F) - logoH * 0.5F + PIVOT_V / LOGO_UNITS_H * logoH;
        return new Matrix4f(pose)
                .translate(Mth.lerp(t, px, w * 0.5F), Mth.lerp(t, py, h * 0.5F), 0.0F)
                .scale(scale, scale, 1.0F)
                .translate(-px, -py, 0.0F);
    }

    private void drawMask(Matrix4f pose, float w, float h, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, mask.getColorTextureId());
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(pose, 0.0F, 0.0F, 0.0F).setUv(0.0F, 1.0F).setColor(alpha, alpha, alpha, alpha);
        b.addVertex(pose, 0.0F, h, 0.0F).setUv(0.0F, 0.0F).setColor(alpha, alpha, alpha, alpha);
        b.addVertex(pose, w, h, 0.0F).setUv(1.0F, 0.0F).setColor(alpha, alpha, alpha, alpha);
        b.addVertex(pose, w, 0.0F, 0.0F).setUv(1.0F, 1.0F).setColor(alpha, alpha, alpha, alpha);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static float logoDrawHeight(float w, float h) {
        return (float) (Math.min(w * 0.75, h) * 0.25);
    }

    /** Same placement as vanilla's {@code LoadingOverlay}; the texture stacks the two word halves. Caller sets the blend. */
    private void drawMojangLogo(Matrix4f pose, float w, float h, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        int textureId = minecraft.getTextureManager().getTexture(MOJANG_LOGO).getId();
        float cx = (int) (w * 0.5F);
        float cy = (int) (h * 0.5F);
        float logoH = logoDrawHeight(w, h);
        float halfW = logoH * 2.0F;
        IntroDraw.textureRegion(pose, textureId, cx - halfW, cy - logoH * 0.5F, halfW, logoH,
                -0.0625F / 120.0F, 0.0F, 119.9375F / 120.0F, 0.5F, alpha);
        IntroDraw.textureRegion(pose, textureId, cx, cy - logoH * 0.5F, halfW, logoH,
                0.0625F / 120.0F, 0.5F, 120.0625F / 120.0F, 1.0F, alpha);
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
        if (mask != null) {
            mask.destroyBuffers();
            mask = null;
        }
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
