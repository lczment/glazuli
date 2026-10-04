package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.LiquidGlass;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix4f;

/**
 * Anti-aliased text for the loading overlays, rasterized at the exact framebuffer pixel size so it stays crisp at
 * any GUI scale. Text appears a frame or two after it is first requested.
 */
final class IntroText {
    private static final int MAX_ENTRIES = 32;
    private static final int PAD = 2;

    private static final Map<Key, Entry> CACHE = new LinkedHashMap<>(16, 0.75F, true);

    private IntroText() {
    }

    /**
     * Draws {@code text} with its box's top edge at {@code y}; {@code align} 0 = left, 0.5 = centered, 1 = right of
     * {@code x}. Use {@link IntroDraw#blendPremultiplied()}. Returns the drawn width in GUI units, 0 if not ready.
     */
    static float draw(Matrix4f pose, String text, float x, float y, float size, IntroRaster.Weight weight, int argb, float align) {
        if (text.isEmpty() || (argb >>> 24) == 0) {
            return 0.0F;
        }
        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        Entry entry = entry(new Key(text, Math.max(4, Math.round(size * scale)), weight));
        if (entry.texture == null) {
            return 0.0F;
        }
        float w = entry.width / scale;
        float h = entry.height / scale;
        float left = snap(x - w * align - PAD / scale, scale);
        float top = snap(y - PAD / scale, scale);
        IntroDraw.texture(pose, entry.texture.getId(), left, top, w, h, argb);
        return w - 2.0F * PAD / scale;
    }

    private static float snap(float v, float scale) {
        return Math.round(v * scale) / scale;
    }

    private static Entry entry(Key key) {
        Entry entry = CACHE.get(key);
        if (entry == null) {
            entry = new Entry();
            CACHE.put(key, entry);
            Entry target = entry;
            IntroRaster.EXECUTOR.execute(() -> target.rasterize(key));
            trim();
        }
        entry.uploadIfReady();
        return entry;
    }

    private static void trim() {
        Iterator<Entry> it = CACHE.values().iterator();
        while (CACHE.size() > MAX_ENTRIES && it.hasNext()) {
            it.next().close();
            it.remove();
        }
    }

    /** Releases all textures. Render thread. */
    static void clear() {
        CACHE.values().forEach(Entry::close);
        CACHE.clear();
    }

    private record Key(String text, int pixelSize, IntroRaster.Weight weight) {
    }

    private static final class Entry {
        @Nullable
        private volatile NativeImage raster;
        private volatile boolean closed;
        @Nullable
        private DynamicTexture texture;
        private int width;
        private int height;

        void rasterize(Key key) {
            try {
                Font font = IntroRaster.font(key.weight, key.pixelSize, key.text);
                BufferedImage probe = IntroRaster.newImage(1, 1);
                Graphics2D pg = IntroRaster.graphics(probe);
                FontMetrics metrics = pg.getFontMetrics(font);
                int w = (int) Math.ceil(metrics.getStringBounds(key.text, pg).getWidth()) + 2 * PAD;
                int h = metrics.getAscent() + metrics.getDescent() + 2 * PAD;
                pg.dispose();

                BufferedImage image = IntroRaster.newImage(w, h);
                Graphics2D g = IntroRaster.graphics(image);
                g.setFont(font);
                g.setColor(java.awt.Color.WHITE);
                g.drawString(key.text, (float) PAD, (float) (PAD + metrics.getAscent()));
                g.dispose();

                NativeImage result = IntroRaster.toNativeImage(image);
                width = w;
                height = h;
                raster = result;
                if (closed) {
                    discardRaster();
                }
            } catch (Throwable t) {
                LiquidGlass.LOGGER.warn("Failed to rasterize intro text '{}'", key.text, t);
            }
        }

        void uploadIfReady() {
            NativeImage image = raster;
            if (texture == null && image != null) {
                raster = null;
                texture = IntroRaster.upload(image);
            }
        }

        private synchronized void discardRaster() {
            NativeImage image = raster;
            raster = null;
            if (image != null) {
                image.close();
            }
        }

        void close() {
            closed = true;
            discardRaster();
            if (texture != null) {
                texture.close();
                texture = null;
            }
        }
    }
}
