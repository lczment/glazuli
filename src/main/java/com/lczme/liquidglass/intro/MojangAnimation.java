package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.anim.AnimClock;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;

/**
 * Mojang Studios' animated logo (the Mojangs tumbling into the letters) with its sound. The first frame replaces the
 * static logo for the whole loading screen; once loading is done and the cave behind is warm (so nothing else hogs the
 * frame) the animation plays, ending on its last frame, which is vanilla's logo that the outro then cuts open.
 * <p>
 * Frames are read straight from the mod file: the first reload hasn't made the mod's resources available yet.
 */
final class MojangAnimation {
    private static final int FRAMES = 39;
    private static final float FRAME_SECONDS = 0.07F;
    private static final float HOLD_SECONDS = 0.5F;
    /** Cross-fade from the early window's static logo to the first frame. */
    private static final float INTRO_FADE_SECONDS = 0.4F;
    /** Uploads per frame, so the loading screen doesn't hitch. */
    private static final int UPLOADS_PER_FRAME = 4;
    /** Once loading is done the outro doesn't wait longer than this for the remaining frames. */
    private static final double LOAD_LIMIT_SECONDS = 4.0;
    private static final ResourceLocation SOUND = LiquidGlass.id("intro.mojang");

    // Where vanilla's logo box (the full 240 x 60 unit logo) lies in a frame, in frame pixels.
    private static final float FRAME_W = 1058.0F;
    private static final float FRAME_H = 280.0F;
    private static final float BOX_X0 = 24.0F;
    private static final float BOX_X1 = 1033.0F;
    private static final float BOX_Y0 = 31.0F;
    private static final float BOX_Y1 = 285.4F;

    private final List<DynamicTexture> textures = new ArrayList<>();
    @Nullable
    private CompletableFuture<List<NativeImage>> decoding;
    private double shownAt = Double.NaN;
    private double waitStart = Double.NaN;
    private double start = Double.NaN;
    private boolean unavailable;

    MojangAnimation() {
    }

    private static NativeImage read(int index) throws IOException {
        var file = ModList.get().getModFileById(LiquidGlass.MODID).getFile();
        try (InputStream in = Files.newInputStream(file.findResource("assets", LiquidGlass.MODID, "textures", "gui", "mojang_logo", "frame_" + index + ".png"))) {
            return NativeImage.read(in);
        }
    }

    private static float length() {
        return FRAMES * FRAME_SECONDS + HOLD_SECONDS;
    }

    /** Shows the first frame right away and decodes the rest off-thread. Render thread; startup only. */
    void load() {
        if (unavailable || !textures.isEmpty()) {
            return;
        }
        try {
            upload(read(0));
        } catch (IOException | RuntimeException e) {
            LiquidGlass.LOGGER.warn("Couldn't load the Mojang logo animation, using the static logo", e);
            unavailable = true;
            return;
        }
        shownAt = AnimClock.now();
        decoding = CompletableFuture.supplyAsync(() -> {
            List<NativeImage> images = new ArrayList<>();
            try {
                for (int i = 1; i < FRAMES; i++) {
                    images.add(read(i));
                }
                return images;
            } catch (IOException e) {
                images.forEach(NativeImage::close);
                throw new RuntimeException(e);
            }
        }, Util.backgroundExecutor());
    }

    private void upload(NativeImage image) {
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        textures.add(texture);
    }

    /** Uploads decoded frames a few per call. */
    void tick() {
        if (decoding == null || !decoding.isDone() || textures.size() >= FRAMES) {
            return;
        }
        if (decoding.isCompletedExceptionally()) {
            LiquidGlass.LOGGER.warn("Couldn't load the Mojang logo animation frames, skipping it", decoding.exceptionNow());
            decoding = null;
            unavailable = true;
            return;
        }
        List<NativeImage> images = decoding.join();
        for (int n = 0; n < UPLOADS_PER_FRAME && textures.size() < FRAMES; n++) {
            upload(images.get(textures.size() - 1));
        }
    }

    /** Plays once every frame is uploaded; gives up (and is finished) if that takes too long. */
    void start() {
        if (isStarted() || unavailable || textures.isEmpty()) {
            if (textures.isEmpty()) {
                unavailable = true;
            }
            return;
        }
        if (textures.size() >= FRAMES) {
            start = AnimClock.now();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(SOUND), 1.0F, 1.0F));
            return;
        }
        if (Double.isNaN(waitStart)) {
            waitStart = AnimClock.now();
        } else if (AnimClock.now() - waitStart > LOAD_LIMIT_SECONDS) {
            unavailable = true;
            LiquidGlass.LOGGER.warn("Mojang logo animation frames took too long to load, skipping it");
        }
    }

    double elapsed() {
        return AnimClock.now() - start;
    }

    boolean isStarted() {
        return !Double.isNaN(start);
    }

    boolean isFinished() {
        return unavailable || isStarted() && AnimClock.now() - start >= length();
    }

    void skip() {
        if (isStarted()) {
            start = AnimClock.now() - length();
        } else {
            unavailable = true;
        }
    }

    /** How much of vanilla's static logo shows: all of it unless a frame is drawn, except while fading to frame 0. */
    float staticLogoAlpha() {
        if (textures.isEmpty() || isFinished()) {
            return 1.0F;
        }
        return isStarted() ? 0.0F : 1.0F - frameZeroAlpha();
    }

    private float frameZeroAlpha() {
        return Math.min(1.0F, (float) (AnimClock.now() - shownAt) / INTRO_FADE_SECONDS);
    }

    /** The current frame over vanilla's logo box ({@code x}, {@code y}, {@code w}, {@code h}); normal blending. */
    void draw(Matrix4f pose, float x, float y, float w, float h, float alpha) {
        if (textures.isEmpty() || isFinished()) {
            return;
        }
        int index = 0;
        float frameAlpha = alpha;
        if (isStarted()) {
            index = Math.min(Math.min(FRAMES, textures.size()) - 1, (int) ((AnimClock.now() - start) / FRAME_SECONDS));
        } else {
            frameAlpha *= frameZeroAlpha();
        }
        float kx = w / (BOX_X1 - BOX_X0);
        float ky = h / (BOX_Y1 - BOX_Y0);
        IntroDraw.textureRegion(pose, textures.get(index).getId(), x - BOX_X0 * kx, y - BOX_Y0 * ky, FRAME_W * kx, FRAME_H * ky,
                0.0F, 0.0F, 1.0F, 1.0F, frameAlpha);
    }

    void close() {
        int uploaded = Math.max(0, textures.size() - 1);
        textures.forEach(DynamicTexture::close);
        textures.clear();
        if (decoding != null) {
            decoding.thenAccept(images -> images.subList(uploaded, images.size()).forEach(NativeImage::close));
            decoding = null;
        }
    }
}
