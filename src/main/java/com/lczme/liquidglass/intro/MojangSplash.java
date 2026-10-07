package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.anim.Timeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * The Mojang Studios loading screen shared by the startup and later resource reloads: brand background, logo and
 * vanilla progress bar, plus the LabyMod-style exit where the letters turn into windows onto whatever is behind and the
 * camera flies through the middle of the logo.
 * <p>
 * Drawing methods expect {@link IntroDraw#begin()} to be active. Until the first reload has finished only
 * {@link IntroDraw} shaders and the Mojang logo texture (registered by vanilla before that reload) may be used.
 */
final class MojangSplash {
    private static final ResourceLocation MOJANG_LOGO = ResourceLocation.withDefaultNamespace("textures/gui/title/mojangstudios.png");
    private static final int BRAND_RED = 0xFFEF323D;
    private static final int BRAND_DARK = 0xFF000000;
    /** Combined logo is 240 x 60 texture units (two stacked 120 x 60 halves side by side). */
    private static final float LOGO_UNITS_W = 240.0F;
    private static final float LOGO_UNITS_H = 60.0F;
    // Middle of a solid letter stroke closest to the logo's centre: the camera flies into it, so the cut-out letter,
    // not the red between letters, fills the screen.
    private static final float PIVOT_U = 117.5F;
    private static final float PIVOT_V = 26.5F;
    private static final float MAX_ZOOM = 80.0F;

    private final Minecraft minecraft;
    final MojangAnimation animation;
    final Timeline outro = new Timeline();
    private final Timeline.Track outroBar = outro.track(1.0F).to(0.0F, 0.25F, 0.0F, Easing.OUT_CUBIC);
    private final Timeline.Track outroCut = outro.track(0.0F).to(0.05F, 0.45F, 1.0F, Easing.IN_OUT_CUBIC);
    private final Timeline.Track outroZoom = outro.track(0.0F).to(0.45F, 1.15F, 1.0F, Easing.IN_OUT_CUBIC);
    private final Timeline.Track outroBackground = outro.track(1.0F).to(1.2F, 0.4F, 0.0F, Easing.IN_CUBIC);

    @Nullable
    private TextureTarget mask;

    MojangSplash(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.animation = new MojangAnimation();
    }

    static int brandColor(Minecraft minecraft) {
        return minecraft.options.darkMojangStudiosBackground().get() ? BRAND_DARK : BRAND_RED;
    }

    /** The plain loading screen; {@code alpha} fades all of it (reloads fade in over the frozen frame). */
    void drawLoading(Matrix4f pose, float w, float h, int background, float progress, float alpha) {
        IntroDraw.fill(pose, 0.0F, 0.0F, w, h, IntroDraw.withAlpha(background, alpha));
        IntroDraw.blendAdditive();
        drawLogo(pose, w, h, alpha * animation.staticLogoAlpha());
        IntroDraw.blendNormal();
        float logoH = logoDrawHeight(w, h);
        animation.draw(pose, (int) (w * 0.5F) - logoH * 2.0F, (int) (h * 0.5F) - logoH * 0.5F, logoH * 4.0F, logoH, alpha);
        drawProgressBar(pose, w, h, progress, alpha * animation.barAlpha());
    }

    /**
     * The exit, over whatever was already drawn this frame. The background is rendered into {@link #mask} with the
     * logo erased from it, so the letters show the scene behind; the result is composited while the camera zooms in.
     */
    void drawOutro(Matrix4f pose, float w, float h, int backgroundColor, float progress) {
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
                // RenderTarget.resize re-enables depth testing; GUI text has already written depth this frame.
                RenderSystem.disableDepthTest();
            }
            mask.setClearColor(FastColor.ARGB32.red(backgroundColor) / 255.0F, FastColor.ARGB32.green(backgroundColor) / 255.0F,
                    FastColor.ARGB32.blue(backgroundColor) / 255.0F, 1.0F);
            mask.clear(Minecraft.ON_OSX);
            mask.bindWrite(true);
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            drawLogo(zoomed, w, h, cut);
            main.bindWrite(true);

            IntroDraw.blendPremultiplied();
            drawMask(pose, w, h, background);
        }
        IntroDraw.blendAdditive();
        drawLogo(zoomed, w, h, 1.0F - cut);
        IntroDraw.blendNormal();
        drawProgressBar(pose, w, h, progress, outroBar.value() * animation.barAlpha());
    }

    void close() {
        animation.close();
        if (mask != null) {
            mask.destroyBuffers();
            mask = null;
        }
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
    private void drawLogo(Matrix4f pose, float w, float h, float alpha) {
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
        float halfW = logoDrawHeight(w, h) * 2.0F;
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
}
