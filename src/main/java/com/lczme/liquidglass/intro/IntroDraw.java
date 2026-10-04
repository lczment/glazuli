package com.lczme.liquidglass.intro;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Immediate-mode drawing that only needs the shaders vanilla preloads before the first resource reload
 * ({@code position_color}, {@code position_tex_color}), so it works at any point of the startup.
 * Wrap calls in {@link #begin()} / {@link #end()}.
 */
final class IntroDraw {
    private static final int CORNER_SEGMENTS = 6;
    private static final int GLOW_SEGMENTS = 32;
    // Approximates a Gaussian falloff with a few linear rings; a single cone looks like a hard-edged disc.
    private static final float[] GLOW_RADII = {0.0F, 0.25F, 0.5F, 0.75F, 1.0F};
    private static final float[] GLOW_ALPHAS = {1.0F, 0.72F, 0.32F, 0.09F, 0.0F};

    private IntroDraw() {
    }

    static void begin() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
    }

    /** Restores what vanilla GUI code expects. */
    static void end() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    static void blendNormal() {
        RenderSystem.defaultBlendFunc();
    }

    static void blendAdditive() {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    /** For textures with premultiplied alpha (all intro textures are). */
    static void blendPremultiplied() {
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    static int withAlpha(int argb, float alpha) {
        int a = Math.round(FastColor.ARGB32.alpha(argb) * Mth.clamp(alpha, 0.0F, 1.0F));
        return FastColor.ARGB32.color(a, argb & 0xFFFFFF);
    }

    /** Vertical gradient rectangle. */
    static void gradient(Matrix4f pose, float x0, float y0, float x1, float y1, int top, int bottom) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        b.addVertex(pose, x0, y0, 0.0F).setColor(top);
        b.addVertex(pose, x0, y1, 0.0F).setColor(bottom);
        b.addVertex(pose, x1, y1, 0.0F).setColor(bottom);
        b.addVertex(pose, x1, y0, 0.0F).setColor(top);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    static void fill(Matrix4f pose, float x0, float y0, float x1, float y1, int argb) {
        gradient(pose, x0, y0, x1, y1, argb, argb);
    }

    /** Rounded rectangle with a horizontal gradient from {@code left} to {@code right}. */
    static void roundedRect(Matrix4f pose, float x, float y, float w, float h, float radius, int left, int right) {
        if (w <= 0.0F || h <= 0.0F) {
            return;
        }
        float r = Math.min(radius, Math.min(w, h) * 0.5F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        vertex(b, pose, x + w * 0.5F, y + h * 0.5F, x, w, left, right);
        corner(b, pose, x + w - r, y + r, r, -90.0F, x, w, left, right);
        corner(b, pose, x + w - r, y + h - r, r, 0.0F, x, w, left, right);
        corner(b, pose, x + r, y + h - r, r, 90.0F, x, w, left, right);
        corner(b, pose, x + r, y + r, r, 180.0F, x, w, left, right);
        vertex(b, pose, x + w - r, y, x, w, left, right);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void corner(BufferBuilder b, Matrix4f pose, float cx, float cy, float r, float startDeg,
                               float x, float w, int left, int right) {
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            float a = (startDeg + 90.0F * i / CORNER_SEGMENTS) * Mth.DEG_TO_RAD;
            vertex(b, pose, cx + Mth.cos(a) * r, cy + Mth.sin(a) * r, x, w, left, right);
        }
    }

    private static void vertex(BufferBuilder b, Matrix4f pose, float vx, float vy, float x, float w, int left, int right) {
        float t = Mth.clamp((vx - x) / w, 0.0F, 1.0F);
        b.addVertex(pose, vx, vy, 0.0F).setColor(FastColor.ARGB32.lerp(t, left, right));
    }

    /** Soft elliptical glow; draw with {@link #blendAdditive()} for light, {@link #blendNormal()} for shade. */
    static void glow(Matrix4f pose, float cx, float cy, float rx, float ry, int argb) {
        if (FastColor.ARGB32.alpha(argb) == 0 || rx <= 0.0F || ry <= 0.0F) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int ring = 0; ring < GLOW_RADII.length - 1; ring++) {
            float r0 = GLOW_RADII[ring];
            float r1 = GLOW_RADII[ring + 1];
            int c0 = withAlpha(argb, GLOW_ALPHAS[ring]);
            int c1 = withAlpha(argb, GLOW_ALPHAS[ring + 1]);
            for (int i = 0; i < GLOW_SEGMENTS; i++) {
                float a0 = Mth.TWO_PI * i / GLOW_SEGMENTS;
                float a1 = Mth.TWO_PI * (i + 1) / GLOW_SEGMENTS;
                float cos0 = Mth.cos(a0) * rx;
                float sin0 = Mth.sin(a0) * ry;
                float cos1 = Mth.cos(a1) * rx;
                float sin1 = Mth.sin(a1) * ry;
                b.addVertex(pose, cx + cos0 * r0, cy + sin0 * r0, 0.0F).setColor(c0);
                b.addVertex(pose, cx + cos0 * r1, cy + sin0 * r1, 0.0F).setColor(c1);
                b.addVertex(pose, cx + cos1 * r1, cy + sin1 * r1, 0.0F).setColor(c1);
                if (ring > 0) {
                    b.addVertex(pose, cx + cos0 * r0, cy + sin0 * r0, 0.0F).setColor(c0);
                    b.addVertex(pose, cx + cos1 * r1, cy + sin1 * r1, 0.0F).setColor(c1);
                    b.addVertex(pose, cx + cos1 * r0, cy + sin1 * r0, 0.0F).setColor(c0);
                }
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /**
     * Textured quad. {@code argb} is a non-premultiplied tint; it is premultiplied here to match the texture,
     * so use {@link #blendPremultiplied()}.
     */
    static void texture(Matrix4f pose, int textureId, float x, float y, float w, float h, int argb) {
        float a = FastColor.ARGB32.alpha(argb) / 255.0F;
        if (a <= 0.0F) {
            return;
        }
        float r = FastColor.ARGB32.red(argb) / 255.0F * a;
        float g = FastColor.ARGB32.green(argb) / 255.0F * a;
        float bl = FastColor.ARGB32.blue(argb) / 255.0F * a;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, textureId);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(pose, x, y, 0.0F).setUv(0.0F, 0.0F).setColor(r, g, bl, a);
        b.addVertex(pose, x, y + h, 0.0F).setUv(0.0F, 1.0F).setColor(r, g, bl, a);
        b.addVertex(pose, x + w, y + h, 0.0F).setUv(1.0F, 1.0F).setColor(r, g, bl, a);
        b.addVertex(pose, x + w, y, 0.0F).setUv(1.0F, 0.0F).setColor(r, g, bl, a);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
}
