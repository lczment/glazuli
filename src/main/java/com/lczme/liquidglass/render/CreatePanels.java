package com.lczme.liquidglass.render;

import com.lczme.liquidglass.LiquidGlass;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Create (and its addons) draw their screens from texture atlases of separate panels, each with a dark outline, a
 * bevel and protrusions (arrows, notches). The panel body, the part of the blitted region that is solidly opaque on
 * most rows and columns, gets a glass panel; the art is redrawn with its frame cut and its colours softened.
 */
public final class CreatePanels {
    public static final GlassStyle PANEL = ContainerGlass.SURFACE;
    public static final int MIN_HEIGHT = 32;
    public static final float FRAME = 3.0F;
    public static final float ART_ALPHA = 0.6F;
    private static final float COVERAGE = 0.85F;
    private static final float[] NONE = new float[0];

    private static final Map<ResourceLocation, Mask> MASKS = new HashMap<>();
    private static final Map<Region, float[]> BODIES = new HashMap<>();

    private CreatePanels() {
    }

    public static ResourceManagerReloadListener reloadListener() {
        return resourceManager -> {
            MASKS.clear();
            BODIES.clear();
        };
    }

    public static boolean isPanelTexture(ResourceLocation texture) {
        String path = texture.getPath();
        return texture.getNamespace().startsWith("create") && path.startsWith("textures/gui/")
                && !path.contains("/ponder/") && !path.contains("icons") && !path.contains("logo")
                && !path.contains("widgets") && !path.contains("sprite") && !path.contains("clipboard");
    }

    /** The panel body as fractions {@code {left, top, right, bottom}} of the blitted region, or null if it has none. */
    public static float[] body(ResourceLocation texture, float u0, float u1, float v0, float v1) {
        float[] body = BODIES.computeIfAbsent(new Region(texture, u0, u1, v0, v1), CreatePanels::findBody);
        return body == NONE ? null : body;
    }

    private static float[] findBody(Region region) {
        Mask mask = MASKS.computeIfAbsent(region.texture, CreatePanels::loadMask);
        if (mask == null) {
            return NONE;
        }
        int x0 = Math.round(Math.min(region.u0, region.u1) * mask.width);
        int x1 = Math.round(Math.max(region.u0, region.u1) * mask.width);
        int y0 = Math.round(Math.min(region.v0, region.v1) * mask.height);
        int y1 = Math.round(Math.max(region.v0, region.v1) * mask.height);
        x0 = Math.max(x0, 0);
        y0 = Math.max(y0, 0);
        x1 = Math.min(x1, mask.width);
        y1 = Math.min(y1, mask.height);
        if (x1 - x0 < 8 || y1 - y0 < 8) {
            return NONE;
        }
        int left = -1;
        int right = -1;
        for (int x = x0; x < x1; x++) {
            int opaque = 0;
            for (int y = y0; y < y1; y++) {
                if (mask.opaque(x, y)) {
                    opaque++;
                }
            }
            if (opaque >= COVERAGE * (y1 - y0)) {
                if (left < 0) {
                    left = x;
                }
                right = x + 1;
            }
        }
        if (left < 0) {
            return NONE;
        }
        int top = -1;
        int bottom = -1;
        for (int y = y0; y < y1; y++) {
            int opaque = 0;
            for (int x = left; x < right; x++) {
                if (mask.opaque(x, y)) {
                    opaque++;
                }
            }
            if (opaque >= COVERAGE * (right - left)) {
                if (top < 0) {
                    top = y;
                }
                bottom = y + 1;
            }
        }
        if (top < 0 || right - left < (x1 - x0) / 2 || bottom - top < (y1 - y0) / 2) {
            return NONE;
        }
        float w = x1 - x0;
        float h = y1 - y0;
        boolean flipX = region.u0 > region.u1;
        boolean flipY = region.v0 > region.v1;
        float l = (left - x0) / w;
        float r = (right - x0) / w;
        float t = (top - y0) / h;
        float b = (bottom - y0) / h;
        return new float[]{flipX ? 1 - r : l, flipY ? 1 - b : t, flipX ? 1 - l : r, flipY ? 1 - t : b};
    }

    private static Mask loadMask(ResourceLocation texture) {
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(texture).orElse(null);
        if (resource == null) {
            return null;
        }
        try (InputStream stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
            int width = image.getWidth();
            int height = image.getHeight();
            boolean[] opaque = new boolean[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    opaque[y * width + x] = (image.getPixelRGBA(x, y) >>> 24) >= 128;
                }
            }
            return new Mask(width, height, opaque);
        } catch (Exception e) {
            LiquidGlass.LOGGER.warn("Could not read {} for glass panels", texture, e);
            return null;
        }
    }

    private record Mask(int width, int height, boolean[] pixels) {
        boolean opaque(int x, int y) {
            return pixels[y * width + x];
        }
    }

    private record Region(ResourceLocation texture, float u0, float u1, float v0, float v1) {
    }
}
