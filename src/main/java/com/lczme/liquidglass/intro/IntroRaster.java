package com.lczme.liquidglass.intro;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Java2D rasterization for intro text and the procedural logo. The game font is not usable during the initial
 * resource load, so text is rendered with system fonts into textures instead. Rasterization runs on a private daemon
 * thread so a slow (or misbehaving) font subsystem can never stall the render thread; results are uploaded later.
 */
final class IntroRaster {
    static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "LiquidGlass intro raster");
        thread.setDaemon(true);
        return thread;
    });

    enum Weight {
        LIGHT("Segoe UI Light", "Segoe UI Semilight", "SF Pro Display Light", "Helvetica Neue Light"),
        REGULAR(),
        SEMIBOLD("Segoe UI Semibold", "SF Pro Display Semibold", "Helvetica Neue Medium");

        private final String[] preferred;

        Weight(String... preferred) {
            this.preferred = preferred;
        }
    }

    private static final String[] REGULAR_FAMILIES = {
            "Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter", "Noto Sans", "Ubuntu", "Cantarell", "DejaVu Sans"
    };

    @Nullable
    private static Set<String> families;

    private IntroRaster() {
    }

    /** Raster thread only. Falls back to the logical Dialog font, which also covers CJK, if the text needs it. */
    static Font font(Weight weight, float size, String text) {
        if (families == null) {
            families = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        }
        Font font = null;
        for (String family : weight.preferred) {
            if (families.contains(family)) {
                font = new Font(family, Font.PLAIN, 1);
                break;
            }
        }
        if (font == null) {
            int style = weight == Weight.SEMIBOLD ? Font.BOLD : Font.PLAIN;
            for (String family : REGULAR_FAMILIES) {
                if (families.contains(family)) {
                    font = new Font(family, style, 1);
                    break;
                }
            }
            if (font == null) {
                font = new Font(Font.SANS_SERIF, style, 1);
            }
        }
        if (font.canDisplayUpTo(text) != -1) {
            font = new Font(Font.DIALOG, font.getStyle(), 1);
        }
        return font.deriveFont(size);
    }

    static BufferedImage newImage(int width, int height) {
        return new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB_PRE);
    }

    static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        return g;
    }

    /** Converts a {@code TYPE_INT_ARGB_PRE} image to a premultiplied ABGR {@link NativeImage}. Any thread. */
    static NativeImage toNativeImage(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] argb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                out.setPixelRGBA(x, y, (c & 0xFF00FF00) | (c & 0xFF) << 16 | (c >> 16) & 0xFF);
            }
        }
        return out;
    }

    /** Premultiplies a straight-alpha ABGR image in place. */
    static void premultiply(NativeImage image) {
        image.applyToAllPixels(c -> {
            int a = c >>> 24;
            if (a == 255) {
                return c;
            }
            int r = (c & 0xFF) * a / 255;
            int g = (c >> 8 & 0xFF) * a / 255;
            int b = (c >> 16 & 0xFF) * a / 255;
            return a << 24 | b << 16 | g << 8 | r;
        });
    }

    /** Render thread. Takes ownership of {@code image}. Linear filtering, clamped edges. */
    static DynamicTexture upload(NativeImage image) {
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return texture;
    }
}
