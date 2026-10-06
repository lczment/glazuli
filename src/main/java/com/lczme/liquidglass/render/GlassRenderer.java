package com.lczme.liquidglass.render;

import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Entry point for drawing liquid glass panels from any GUI code.
 * <p>
 * Draw order matters: everything submitted to the {@link GuiGraphics} before {@link #drawPanel} ends up beneath
 * the glass. The backdrop is captured once per frame on the first panel, so overlapping panels drawn later in the
 * same frame do not see earlier panels through their glass unless {@link #recaptureBackdrop} is called in between.
 */
public final class GlassRenderer {
    /** Quad padding around the panel for the drop shadow; must cover the shader's shadow extent + offset. */
    private static final float SHADOW_MARGIN = 8.0F;
    private static final int FALLBACK_BASE = GlassStyle.FILM_COLOR;
    private static final float FALLBACK_GLASS_BODY = 0.62F;
    private static final int CORNER_SEGMENTS = 8;

    private GlassRenderer() {
    }

    /** Whether the full shader effect is used; otherwise {@link #drawPanel} draws a flat translucent fallback. */
    public static boolean isGlassAvailable() {
        return LiquidGlassConfig.ENABLE_GLASS.get()
                && LiquidGlassConfig.BLUR_QUALITY.get() != LiquidGlassConfig.BlurQuality.OFF
                && LiquidGlassShaders.isReady();
    }

    /**
     * Forces a fresh backdrop capture so that subsequent panels blur everything drawn so far, including other panels.
     * Use for stacked UI such as popups, dropdowns or tooltips above a glass panel. Costs one full blur chain.
     */
    public static void recaptureBackdrop(GuiGraphics guiGraphics) {
        if (isGlassAvailable()) {
            BlurPipeline.capture(guiGraphics);
        }
    }

    /** Draws a glass panel at GUI coordinates, honoring the current {@link GuiGraphics#pose()} transform. */
    public static void drawPanel(GuiGraphics guiGraphics, float x, float y, float width, float height, GlassStyle style) {
        drawPanel(guiGraphics, x, y, width, height, style, false);
    }

    /** {@code depthTested}: anything already drawn at a higher z than the pose's stays in front of the panel. */
    public static void drawPanel(GuiGraphics guiGraphics, float x, float y, float width, float height, GlassStyle style,
                                 boolean depthTested) {
        if (width <= 0.0F || height <= 0.0F || style.opacity() <= 0.0F) {
            return;
        }
        if (!isGlassAvailable()) {
            drawFallback(guiGraphics, x, y, width, height, style);
            return;
        }

        BlurPipeline.ensureCaptured(guiGraphics);
        guiGraphics.flush();

        ShaderInstance shader = LiquidGlassShaders.glassPanel();
        boolean film = style.material() == GlassStyle.Material.FILM;
        shader.safeGetUniform("RectSize").set(width, height);
        shader.safeGetUniform("Radius").set(style.cornerRadius());
        shader.safeGetUniform("Tint").set(
                FastColor.ARGB32.red(style.color()) / 255.0F,
                FastColor.ARGB32.green(style.color()) / 255.0F,
                FastColor.ARGB32.blue(style.color()) / 255.0F,
                film ? style.bodyOpacity() : style.tint());
        shader.safeGetUniform("Shift").set(LiquidGlassConfig.ENABLE_REFRACTION.get() ? style.shift() : 0.0F);
        shader.safeGetUniform("Falloff").set(style.falloff());
        shader.safeGetUniform("RimFrac").set(style.rimFrac());
        shader.safeGetUniform("Material").set(film ? 1.0F : 0.0F);
        shader.safeGetUniform("Shadow").set(style.shadow());
        shader.safeGetUniform("Opacity").set(Mth.clamp(style.opacity(), 0.0F, 1.0F));
        shader.safeGetUniform("Specular").set(film ? 0.0F : style.specular());
        shader.safeGetUniform("Highlight").set(Mth.clamp(style.hover(), 0.0F, 1.0F));

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, style.blur() ? BlurPipeline.blurredTexture() : BlurPipeline.sharpTexture());
        RenderSystem.enableBlend();
        // The shader outputs premultiplied alpha so the AA edge and the outer shadow composite correctly.
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        if (depthTested) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }

        float m = style.shadow() > 0.0F ? SHADOW_MARGIN : 1.0F;
        Matrix4f pose = guiGraphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(pose, x - m, y - m, 0.0F).setUv(-m, -m);
        builder.addVertex(pose, x - m, y + height + m, 0.0F).setUv(-m, height + m);
        builder.addVertex(pose, x + width + m, y + height + m, 0.0F).setUv(width + m, height + m);
        builder.addVertex(pose, x + width + m, y - m, 0.0F).setUv(width + m, -m);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        restoreGuiState();
    }

    private static void drawFallback(GuiGraphics guiGraphics, float x, float y, float width, float height, GlassStyle style) {
        guiGraphics.flush();
        // Spec fallback: a translucent body plus a 1px inner ring (dark body on this platform, see GlassStyle).
        float opacity = Mth.clamp(style.opacity(), 0.0F, 1.0F);
        boolean film = style.material() == GlassStyle.Material.FILM;
        int rgb = film ? style.color() : FALLBACK_BASE;
        float body = film ? style.bodyOpacity() : FALLBACK_GLASS_BODY;
        int fill = FastColor.ARGB32.color(Math.round(body * opacity * 255.0F), rgb);
        int highlight = FastColor.ARGB32.color(Math.round(0.95F * opacity * 255.0F), 0xFFFFFF);

        float r = Math.min(style.cornerRadius(), Math.min(width, height) * 0.5F);
        fillRounded(guiGraphics, x, y, width, height, r, fill);

        if (highlight >>> 24 != 0 && width > 2.0F * r) {
            Matrix4f pose = guiGraphics.pose().last().pose();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            BufferBuilder line = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            line.addVertex(pose, x + r, y, 0.0F).setColor(highlight);
            line.addVertex(pose, x + r, y + 1.0F, 0.0F).setColor(highlight);
            line.addVertex(pose, x + width - r, y + 1.0F, 0.0F).setColor(highlight);
            line.addVertex(pose, x + width - r, y, 0.0F).setColor(highlight);
            BufferUploader.drawWithShader(line.buildOrThrow());
        }

        restoreGuiState();
    }

    /** Flat anti-alias-free rounded rectangle in ARGB, at GUI coordinates (honours the pose). */
    public static void fillRounded(GuiGraphics guiGraphics, float x, float y, float width, float height, float radius, int argb) {
        if (width <= 0.0F || height <= 0.0F || argb >>> 24 == 0) {
            return;
        }
        guiGraphics.flush();
        float r = Math.min(radius, Math.min(width, height) * 0.5F);
        Matrix4f pose = guiGraphics.pose().last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        builder.addVertex(pose, x + width * 0.5F, y + height * 0.5F, 0.0F).setColor(argb);
        // Same winding as vanilla quads (top-left, bottom-left, bottom-right, top-right) so back-face culling keeps it.
        addCorner(builder, pose, x + r, y + r, r, 270.0F, argb);
        addCorner(builder, pose, x + r, y + height - r, r, 180.0F, argb);
        addCorner(builder, pose, x + width - r, y + height - r, r, 90.0F, argb);
        addCorner(builder, pose, x + width - r, y + r, r, 0.0F, argb);
        builder.addVertex(pose, x + r, y, 0.0F).setColor(argb);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        restoreGuiState();
    }

    /** Emits a quarter circle from {@code startDeg} going counter-clockwise on screen (decreasing angle, Y down). */
    private static void addCorner(BufferBuilder builder, Matrix4f pose, float cx, float cy, float r, float startDeg, int color) {
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            float a = (startDeg - 90.0F * i / CORNER_SEGMENTS) * Mth.DEG_TO_RAD;
            builder.addVertex(pose, cx + Mth.cos(a) * r, cy + Mth.sin(a) * r, 0.0F).setColor(color);
        }
    }

    /** Leaves the state the way {@link GuiGraphics#flush()} does, which is what vanilla GUI code expects. */
    private static void restoreGuiState() {
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}
