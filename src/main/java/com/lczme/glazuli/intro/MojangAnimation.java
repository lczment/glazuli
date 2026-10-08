package com.lczme.glazuli.intro;

import com.lczme.glazuli.Glazuli;
import com.lczme.glazuli.anim.AnimClock;
import com.lczme.glazuli.dev.DevHooks;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
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
    /** The original 39 frames at 14 fps, interpolated four times over (RIFE) to 57 fps. */
    private static final int FRAMES = 153;
    private static final float FRAME_SECONDS = 0.0175F;
    /** How long the last frame stays, to the end of the sound. */
    private static final float LAST_FRAME_SECONDS = 0.57F;
    /** Frames decoded ahead of the one shown; only the shown frame is on the GPU. */
    private static final int LOOKAHEAD = 12;
    /** Cross-fade from the early window's static logo to the first frame. */
    private static final float INTRO_FADE_SECONDS = 0.4F;
    private static final float BAR_FADE_SECONDS = 0.25F;
    private static final float HANDOVER_SECONDS = 0.2F;
    /** Once loading is done the outro doesn't wait longer than this for the first frames. */
    private static final double LOAD_LIMIT_SECONDS = 4.0;
    private static final ResourceLocation SOUND = Glazuli.id("intro.mojang");

    // Where vanilla's logo box (the full 240 x 60 unit logo) lies in a frame, in frame pixels.
    private static final float FRAME_W = 1058.0F;
    private static final float FRAME_H = 280.0F;
    private static final float BOX_X0 = 24.0F;
    private static final float BOX_X1 = 1033.0F;
    private static final float BOX_Y0 = 31.17F;
    private static final float BOX_Y1 = 281.88F;

    /** Holds the shown frame; later frames replace its pixels as they come up. */
    @Nullable
    private DynamicTexture texture;
    private int shown;
    /** The frames' PNG files (grey and alpha), read off-thread; decoding all of them up front would take ~180 MB. */
    @Nullable
    private CompletableFuture<byte[][]> reading;
    private final List<CompletableFuture<NativeImage>> decoded = new ArrayList<>(Collections.nCopies(FRAMES, null));
    private double shownAt = Double.NaN;
    private double waitStart = Double.NaN;
    private double start = Double.NaN;
    private boolean unavailable;

    MojangAnimation() {
    }

    private static byte[] file(int index) throws IOException {
        var file = ModList.get().getModFileById(Glazuli.MODID).getFile();
        return Files.readAllBytes(file.findResource("assets", Glazuli.MODID, "textures", "gui", "mojang_logo", "frame_" + index + ".png"));
    }

    private static NativeImage decode(byte[] png) {
        try {
            return NativeImage.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static float length() {
        return (FRAMES - 1) * FRAME_SECONDS + LAST_FRAME_SECONDS;
    }

    /** Shows the first frame right away and reads the rest off-thread. Render thread; startup only. */
    void load() {
        if (unavailable || texture != null) {
            return;
        }
        try {
            texture = new DynamicTexture(decode(file(0)));
            texture.setFilter(true, false);
        } catch (IOException | RuntimeException e) {
            Glazuli.LOGGER.warn("Couldn't load the Mojang logo animation, using the static logo", e);
            unavailable = true;
            return;
        }
        shownAt = AnimClock.now();
        reading = CompletableFuture.supplyAsync(() -> {
            byte[][] files = new byte[FRAMES][];
            try {
                for (int i = 1; i < FRAMES; i++) {
                    files[i] = file(i);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return files;
        }, Util.backgroundExecutor());
    }

    private int currentFrame() {
        return isStarted() ? Math.min(FRAMES - 1, (int) (elapsed() / FRAME_SECONDS)) : 0;
    }

    /** Keeps the next few frames decoding. */
    void tick() {
        if (reading == null || !reading.isDone()) {
            return;
        }
        if (reading.isCompletedExceptionally()) {
            Glazuli.LOGGER.warn("Couldn't load the Mojang logo animation frames, skipping it", reading.exceptionNow());
            reading = null;
            unavailable = true;
            return;
        }
        byte[][] files = reading.join();
        int from = Math.max(currentFrame(), shown + 1);
        for (int i = from; i <= Math.min(FRAMES - 1, from + LOOKAHEAD); i++) {
            if (decoded.get(i) == null) {
                byte[] png = files[i];
                decoded.set(i, CompletableFuture.supplyAsync(() -> decode(png), Util.backgroundExecutor()));
            }
        }
    }

    private boolean firstFramesReady() {
        for (int i = 1; i <= LOOKAHEAD; i++) {
            CompletableFuture<NativeImage> frame = decoded.get(i);
            if (frame == null || !frame.isDone()) {
                return false;
            }
        }
        return true;
    }

    /** Swaps in the newest decoded frame up to the current one (frames still decoding are skipped, not waited for). */
    private void advance() {
        int target = currentFrame();
        int next = -1;
        for (int i = target; i > shown; i--) {
            CompletableFuture<NativeImage> frame = decoded.get(i);
            if (frame != null && frame.isDone() && !frame.isCompletedExceptionally()) {
                next = i;
                break;
            }
        }
        if (next < 0 || texture == null) {
            return;
        }
        for (int i = shown + 1; i < next; i++) {
            CompletableFuture<NativeImage> skipped = decoded.set(i, null);
            if (skipped != null) {
                skipped.thenAccept(NativeImage::close);
            }
        }
        NativeImage image = decoded.set(next, null).join();
        texture.setPixels(image);
        texture.bind();
        image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), true, false, false, false);
        shown = next;
    }

    /** Plays once the first frames are decoded; gives up (and is finished) if that takes too long. */
    void start() {
        if (isStarted() || unavailable || texture == null) {
            if (texture == null) {
                unavailable = true;
            }
            return;
        }
        if (firstFramesReady()) {
            start = AnimClock.now();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(SOUND), 1.0F, 1.0F));
            return;
        }
        if (Double.isNaN(waitStart)) {
            waitStart = AnimClock.now();
        } else if (AnimClock.now() - waitStart > LOAD_LIMIT_SECONDS) {
            unavailable = true;
            Glazuli.LOGGER.warn("Mojang logo animation frames took too long to load, skipping it");
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

    double remaining() {
        return isStarted() ? length() - elapsed() : length();
    }

    /** The progress bar leaves as the animation starts. */
    float barAlpha() {
        return isStarted() ? Math.max(0.0F, 1.0F - (float) elapsed() / BAR_FADE_SECONDS) : 1.0F;
    }

    private boolean showsFrame() {
        return texture != null && !isFinished() && !(isStarted() && DevHooks.forceStaticLogo(elapsed()));
    }

    /** How much of vanilla's static logo shows: all of it unless a frame is drawn, except while fading to frame 0. */
    float staticLogoAlpha() {
        if (!showsFrame()) {
            return 1.0F;
        }
        return isStarted() ? handoverAlpha() : 1.0F - frameZeroAlpha();
    }

    /** The last frame and vanilla's logo differ by a pixel along some edges (rasterised apart), so they cross-fade. */
    private float handoverAlpha() {
        float t = (float) elapsed() - (length() - HANDOVER_SECONDS);
        return Math.max(0.0F, Math.min(1.0F, t / HANDOVER_SECONDS));
    }

    private float frameZeroAlpha() {
        return Math.min(1.0F, (float) (AnimClock.now() - shownAt) / INTRO_FADE_SECONDS);
    }

    /** The current frame over vanilla's logo box ({@code x}, {@code y}, {@code w}, {@code h}); normal blending. */
    void draw(Matrix4f pose, float x, float y, float w, float h, float alpha) {
        if (!showsFrame()) {
            return;
        }
        float frameAlpha = alpha;
        if (isStarted()) {
            advance();
            frameAlpha *= 1.0F - handoverAlpha();
        } else {
            frameAlpha *= frameZeroAlpha();
        }
        float kx = w / (BOX_X1 - BOX_X0);
        float ky = h / (BOX_Y1 - BOX_Y0);
        IntroDraw.textureRegion(pose, texture.getId(), x - BOX_X0 * kx, y - BOX_Y0 * ky, FRAME_W * kx, FRAME_H * ky,
                0.0F, 0.0F, 1.0F, 1.0F, frameAlpha);
    }

    void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        for (int i = 0; i < FRAMES; i++) {
            CompletableFuture<NativeImage> frame = decoded.set(i, null);
            if (frame != null) {
                frame.thenAccept(NativeImage::close);
            }
        }
        reading = null;
    }
}
