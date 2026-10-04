package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.LiquidGlass;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;

/**
 * The brand logo. Uses {@code assets/liquidglass/textures/gui/logo.png} if present (also overridable by resource
 * packs), otherwise a procedural glass-orb emblem with a "Liquid Glass" wordmark.
 * <p>
 * The texture is created outside the {@link net.minecraft.client.renderer.texture.TextureManager}, which is not
 * ready during the initial load, and is (re)built at the drawn pixel height so it stays sharp at every GUI scale.
 */
public final class IntroLogo {
    public static final ResourceLocation LOCATION = LiquidGlass.id("textures/gui/logo.png");
    private static final float DEFAULT_ASPECT = 4.2F;
    private static final int SHEEN_STRIPS = 48;

    private static boolean sourceLoaded;
    @Nullable
    private static NativeImage fileImage;

    @Nullable
    private static DynamicTexture texture;
    private static int texturePixelHeight;
    private static float aspect = DEFAULT_ASPECT;

    @Nullable
    private static CompletableFuture<NativeImage> pending;
    private static int pendingPixelHeight;
    private static boolean failed;

    private IntroLogo() {
    }

    /** Width / height of the logo box. */
    public static float aspect() {
        return aspect;
    }

    /** Whether a texture is available; call {@link #prepare} first. */
    public static boolean isReady() {
        return texture != null || failed;
    }

    /** Makes sure a texture for drawing at {@code height} GUI units is (being) built. Render thread. */
    public static void prepare(float height) {
        int px = Math.max(8, Math.round(height * (float) Minecraft.getInstance().getWindow().getGuiScale()));
        if (pending != null && pending.isDone()) {
            NativeImage image = pending.getNow(null);
            pending = null;
            if (image != null) {
                replaceTexture(image, pendingPixelHeight);
            } else if (texture == null) {
                failed = true;
            }
        }
        if (failed || px == texturePixelHeight || pending != null && px == pendingPixelHeight) {
            return;
        }
        if (!sourceLoaded) {
            sourceLoaded = true;
            fileImage = loadFile();
        }
        if (fileImage != null) {
            replaceTexture(scaledFileImage(fileImage, px), px);
        } else if (pending == null) {
            pendingPixelHeight = px;
            pending = CompletableFuture.supplyAsync(() -> renderProcedural(px), IntroRaster.EXECUTOR)
                    .exceptionally(t -> {
                        LiquidGlass.LOGGER.warn("Failed to render the procedural intro logo", t);
                        return null;
                    });
        }
    }

    /**
     * Draws the logo centered at ({@code cx}, {@code cy}) with the given box height in GUI units. {@code sheen} in
     * [0, 1] is the position of a diagonal highlight sweeping across it (outside that range: no sheen).
     */
    public static void draw(Matrix4f pose, float cx, float cy, float height, float alpha, float sheen) {
        if (alpha <= 0.0F) {
            return;
        }
        IntroDraw.begin();
        float w = height * aspect;
        float x = cx - w * 0.5F;
        float y = cy - height * 0.5F;
        if (texture != null) {
            IntroDraw.blendPremultiplied();
            IntroDraw.texture(pose, texture.getId(), x, y, w, height, IntroDraw.withAlpha(0xFFFFFFFF, alpha));
            if (sheen > -0.5F && sheen < 1.5F) {
                drawSheen(pose, texture.getId(), x, y, w, height, sheen, alpha);
            }
        } else if (failed) {
            // Last resort without Java2D: a soft orb so the screen is never empty.
            IntroDraw.blendAdditive();
            IntroDraw.glow(pose, cx, cy, height * 0.6F, height * 0.6F, IntroDraw.withAlpha(0xFF6F8CFF, alpha * 0.9F));
            IntroDraw.glow(pose, cx - height * 0.12F, cy - height * 0.14F, height * 0.22F, height * 0.16F, IntroDraw.withAlpha(0xFFFFFFFF, alpha * 0.6F));
        }
        IntroDraw.end();
    }

    /** Diagonal band of extra light, built from vertex-colored strips so it needs no mask texture or custom shader. */
    private static void drawSheen(Matrix4f pose, int textureId, float x, float y, float w, float h, float center, float alpha) {
        float skew = 0.18F;
        float bandWidth = 0.09F;
        // Additive with a premultiplied texture: src = tex.rgb * c, so c scales the added light directly.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, textureId);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float c0 = center * (1.0F + 2.0F * skew) - skew;
        for (int i = 0; i < SHEEN_STRIPS; i++) {
            float u0 = (float) i / SHEEN_STRIPS;
            float u1 = (float) (i + 1) / SHEEN_STRIPS;
            float t0 = band(u0 + skew * 0.5F, c0, bandWidth) * alpha * 0.55F;
            float b0 = band(u0 - skew * 0.5F, c0, bandWidth) * alpha * 0.55F;
            float t1 = band(u1 + skew * 0.5F, c0, bandWidth) * alpha * 0.55F;
            float b1 = band(u1 - skew * 0.5F, c0, bandWidth) * alpha * 0.55F;
            b.addVertex(pose, x + w * u0, y, 0.0F).setUv(u0, 0.0F).setColor(t0, t0, t0, t0);
            b.addVertex(pose, x + w * u0, y + h, 0.0F).setUv(u0, 1.0F).setColor(b0, b0, b0, b0);
            b.addVertex(pose, x + w * u1, y + h, 0.0F).setUv(u1, 1.0F).setColor(b1, b1, b1, b1);
            b.addVertex(pose, x + w * u1, y, 0.0F).setUv(u1, 0.0F).setColor(t1, t1, t1, t1);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static float band(float u, float center, float width) {
        float d = (u - center) / width;
        return (float) Math.exp(-d * d);
    }

    private static void replaceTexture(NativeImage image, int pixelHeight) {
        if (texture != null) {
            texture.close();
        }
        texture = IntroRaster.upload(image);
        texturePixelHeight = pixelHeight;
        aspect = (float) image.getWidth() / image.getHeight();
    }

    @Nullable
    private static NativeImage loadFile() {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(LOCATION);
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream in = resource.get().open()) {
                NativeImage image = NativeImage.read(NativeImage.Format.RGBA, in);
                LiquidGlass.LOGGER.info("Using intro logo {} ({}x{})", LOCATION, image.getWidth(), image.getHeight());
                return image;
            }
        } catch (Exception e) {
            LiquidGlass.LOGGER.warn("Failed to load intro logo {}, using the procedural one", LOCATION, e);
            return null;
        }
    }

    private static NativeImage scaledFileImage(NativeImage source, int pixelHeight) {
        int h = Math.min(pixelHeight, source.getHeight());
        int w = Math.max(1, Math.round(source.getWidth() * (float) h / source.getHeight()));
        NativeImage out = new NativeImage(w, h, false);
        if (h == source.getHeight()) {
            out.copyFrom(source);
        } else {
            source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), out);
        }
        IntroRaster.premultiply(out);
        return out;
    }

    /** Glass orb emblem + wordmark, drawn at {@code px} pixels tall. Raster thread. */
    private static NativeImage renderProcedural(int px) {
        String first = "Liquid";
        String second = " Glass";
        float fontSize = px * 0.5F;
        Font bold = IntroRaster.font(IntroRaster.Weight.SEMIBOLD, fontSize, first);
        Font light = IntroRaster.font(IntroRaster.Weight.LIGHT, fontSize, second);
        FontRenderContext frc = new FontRenderContext(null, true, true);
        TextLayout firstLayout = new TextLayout(first, bold, frc);
        TextLayout secondLayout = new TextLayout(second, light, frc);
        float capHeight = (float) new TextLayout("L", bold, frc).getBounds().getHeight();

        int pad = Math.max(2, Math.round(px * 0.06F));
        float d = px - 2.0F * pad;
        float gap = px * 0.26F;
        float textWidth = firstLayout.getAdvance() + secondLayout.getAdvance();
        int width = Math.round(pad + d + gap + textWidth + pad);
        BufferedImage image = IntroRaster.newImage(width, px);
        Graphics2D g = IntroRaster.graphics(image);

        float r = d * 0.5F;
        float cx = pad + r;
        float cy = px * 0.5F;
        Ellipse2D.Float orb = new Ellipse2D.Float(cx - r, cy - r, d, d);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx - r * 0.25F, cy - r * 0.3F), r * 1.35F,
                new float[]{0.0F, 0.5F, 1.0F},
                new Color[]{new Color(156, 205, 255, 235), new Color(98, 124, 246, 228), new Color(118, 82, 224, 220)}));
        g.fill(orb);
        g.setClip(orb);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx + r * 0.45F, cy + r * 0.55F), r * 0.95F,
                new float[]{0.0F, 1.0F}, new Color[]{new Color(70, 226, 214, 190), new Color(70, 226, 214, 0)}));
        g.fill(orb);
        // Darker core makes the rim read as thick glass.
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy + r * 0.1F), r * 0.8F,
                new float[]{0.0F, 1.0F}, new Color[]{new Color(20, 24, 60, 70), new Color(20, 24, 60, 0)}));
        g.fill(orb);
        AffineTransform saved = g.getTransform();
        g.rotate(-0.55, cx, cy);
        Ellipse2D.Float highlight = new Ellipse2D.Float(cx - r * 0.62F, cy - r * 0.86F, r * 1.24F, r * 0.62F);
        g.setPaint(new GradientPaint(cx, cy - r * 0.86F, new Color(255, 255, 255, 215), cx, cy - r * 0.24F, new Color(255, 255, 255, 0)));
        g.fill(highlight);
        g.setTransform(saved);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx + r * 0.2F, cy + r * 0.78F), r * 0.45F,
                new float[]{0.0F, 1.0F}, new Color[]{new Color(255, 255, 255, 110), new Color(255, 255, 255, 0)}));
        g.fill(orb);
        g.setClip(null);
        float stroke = Math.max(1.0F, d * 0.04F);
        g.setStroke(new BasicStroke(stroke));
        g.setPaint(new GradientPaint(cx, cy - r, new Color(255, 255, 255, 220), cx, cy + r, new Color(200, 225, 255, 60)));
        g.draw(new Ellipse2D.Float(cx - r + stroke * 0.5F, cy - r + stroke * 0.5F, d - stroke, d - stroke));

        float textX = pad + d + gap;
        float baseline = cy + capHeight * 0.5F;
        g.setPaint(new GradientPaint(0, baseline - capHeight, new Color(255, 255, 255), 0, baseline, new Color(214, 226, 255)));
        firstLayout.draw(g, textX, baseline);
        g.setPaint(new GradientPaint(0, baseline - capHeight, new Color(226, 234, 255, 225), 0, baseline, new Color(176, 196, 240, 225)));
        secondLayout.draw(g, textX + firstLayout.getAdvance(), baseline);
        g.dispose();
        return IntroRaster.toNativeImage(image);
    }

    /** Frees the texture; it is rebuilt on the next {@link #prepare}. Render thread. */
    public static void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        texturePixelHeight = 0;
    }
}
