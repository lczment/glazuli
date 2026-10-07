package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.anim.AnimClock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.joml.Matrix4f;

/**
 * Mojang Studios' animated logo (the Mojangs tumbling into the letters) with its sound, played once loading is done and
 * before the logo opens onto the cave. It starts from the static logo and ends on it: the first frame fades in over the
 * logo, the last frame (the finished logo) fades back into vanilla's texture, which the outro then cuts open.
 */
final class MojangAnimation {
    private static final int FRAMES = 39;
    private static final float FRAME_SECONDS = 0.07F;
    private static final float FADE_IN_SECONDS = 0.2F;
    private static final float HOLD_SECONDS = 0.5F;
    private static final float FADE_OUT_SECONDS = 0.25F;
    /** Frames start early only if their textures are ready; the outro doesn't wait longer than this for them. */
    private static final double PRELOAD_LIMIT_SECONDS = 4.0;
    private static final ResourceLocation SOUND = LiquidGlass.id("intro.mojang");

    // Where vanilla's logo box (the full 240 x 60 unit logo) lies in a frame, in frame pixels.
    private static final float FRAME_W = 1058.0F;
    private static final float FRAME_H = 280.0F;
    private static final float BOX_X0 = 24.0F;
    private static final float BOX_X1 = 1033.0F;
    private static final float BOX_Y0 = 31.0F;
    private static final float BOX_Y1 = 285.4F;

    private final Minecraft minecraft;
    private final List<CompletableFuture<Void>> preloads = new ArrayList<>();
    private double preloadStart = Double.NaN;
    private double start = Double.NaN;
    private boolean unavailable;

    MojangAnimation(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    private static ResourceLocation frame(int index) {
        return LiquidGlass.id("textures/gui/mojang_logo/frame_" + index + ".png");
    }

    private static float length() {
        return FADE_IN_SECONDS + FRAMES * FRAME_SECONDS + HOLD_SECONDS;
    }

    /** Loads the frames off-thread; needs the mod's resources, i.e. the first reload applied. */
    void preload() {
        if (!preloads.isEmpty()) {
            return;
        }
        preloadStart = AnimClock.now();
        for (int i = 0; i < FRAMES; i++) {
            preloads.add(minecraft.getTextureManager().preload(frame(i), Util.backgroundExecutor()));
        }
    }

    /** Starts once the frames are loaded; gives up (and is finished) if they don't load in time or failed. */
    void update() {
        if (isStarted() || unavailable || preloads.isEmpty()) {
            return;
        }
        boolean ready = preloads.stream().allMatch(CompletableFuture::isDone);
        if (ready && preloads.stream().noneMatch(CompletableFuture::isCompletedExceptionally)) {
            for (int i = 0; i < FRAMES; i++) {
                minecraft.getTextureManager().getTexture(frame(i)).setFilter(true, false);
            }
            start = AnimClock.now();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(SOUND), 1.0F, 1.0F));
        } else if (ready || AnimClock.now() - preloadStart > PRELOAD_LIMIT_SECONDS) {
            unavailable = true;
            LiquidGlass.LOGGER.warn("Mojang logo animation frames didn't load, skipping it");
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

    /** How much of vanilla's static logo still shows (1 before and after the animation). */
    float staticLogoAlpha() {
        if (!isStarted() || isFinished()) {
            return 1.0F;
        }
        float t = (float) (AnimClock.now() - start);
        if (t < FADE_IN_SECONDS) {
            return 1.0F - t / FADE_IN_SECONDS;
        }
        float fadeOutStart = length() - FADE_OUT_SECONDS;
        return t > fadeOutStart ? (t - fadeOutStart) / FADE_OUT_SECONDS : 0.0F;
    }

    /** The current frame over vanilla's logo box ({@code x}, {@code y}, {@code w}, {@code h}); normal blending. */
    void draw(Matrix4f pose, float x, float y, float w, float h, float alpha) {
        if (!isStarted() || isFinished()) {
            return;
        }
        float t = (float) (AnimClock.now() - start);
        int index = Math.min(FRAMES - 1, Math.max(0, (int) ((t - FADE_IN_SECONDS) / FRAME_SECONDS)));
        float frameAlpha = alpha * (t < FADE_IN_SECONDS ? t / FADE_IN_SECONDS : 1.0F - staticLogoAlpha());
        AbstractTexture texture = minecraft.getTextureManager().getTexture(frame(index));
        float kx = w / (BOX_X1 - BOX_X0);
        float ky = h / (BOX_Y1 - BOX_Y0);
        IntroDraw.textureRegion(pose, texture.getId(), x - BOX_X0 * kx, y - BOX_Y0 * ky, FRAME_W * kx, FRAME_H * ky,
                0.0F, 0.0F, 1.0F, 1.0F, frameAlpha);
    }
}
