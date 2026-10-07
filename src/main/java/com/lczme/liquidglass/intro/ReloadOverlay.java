package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.cave.CaveBackground;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Resource reloads after startup (resource packs, language, F3+T) use the same Mojang Studios screen as the startup:
 * it fades in over the frozen frame, shows progress, and exits by flying through the logo onto the live screen.
 * Freezing matters because textures, models and fonts are swapped while the reload runs, so live screen/world content
 * is not safe to show until the reload has finished (vanilla covers it completely for the same reason).
 */
public final class ReloadOverlay extends GlassLoadingOverlay {
    private static final float FADE_IN_SECONDS = 0.35F;
    /** Vanilla keeps a fading-in overlay up for at least one second; ours is a bit snappier but still avoids a flash. */
    private static final float MIN_SECONDS_WITH_FADE_IN = 0.8F;
    private static final float MAX_FILL_SECONDS = 0.6F;
    private static final float MAX_BACKDROP_WAIT_SECONDS = 2.0F;
    private static final int FREEZE_FALLBACK = 0xFF0B0D12;

    private final MojangSplash splash;
    @Nullable
    private TextureTarget frozen;
    private boolean frozenTaken;
    private double fillStart = Double.NaN;
    private boolean released;

    ReloadOverlay(Minecraft minecraft, ReloadInstance reload, Consumer<Optional<Throwable>> onFinish, boolean fadeIn) {
        super(minecraft, reload, onFinish, fadeIn);
        this.splash = new MojangSplash(minecraft);
        // Reloads usually start between frames, while the main target still holds the last presented frame. Callers
        // often switch screens right after starting the reload (the language screen returns to its parent), so
        // freezing on our first frame would flash the next screen; freeze what the player was actually looking at.
        if (RenderSystem.isOnRenderThread()) {
            frozenTaken = true;
            frozen = snapshot();
        }
    }

    /** Seconds since the exit animation started, or a negative value before that. */
    public float fadeOutElapsed() {
        return splash.outro.isStarted() ? splash.outro.elapsed() : -1.0F;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (released) {
            return;
        }
        markFrame();
        float t = elapsed();
        float progress = bar.update(reload.getActualProgress(), reload.isDone());
        if (!frozenTaken) {
            freeze(guiGraphics, mouseX, mouseY, partialTick);
        }

        if (!isFinishCalled() && reload.isDone() && (!fadeIn || t >= MIN_SECONDS_WITH_FADE_IN)) {
            finishReload(guiGraphics);
            fillStart = AnimClock.now();
            if (isReplaced()) {
                release();
                return;
            }
        }
        if (isFinishCalled() && !splash.outro.isStarted()) {
            CaveBackground.prepare(true);
            double sinceFill = AnimClock.now() - fillStart;
            boolean filled = progress >= 1.0F || sinceFill >= MAX_FILL_SECONDS;
            boolean backdropReady = CaveBackground.isUpToDate() || sinceFill >= MAX_BACKDROP_WAIT_SECONDS;
            if (filled && backdropReady) {
                splash.outro.start();
            }
        }

        float w = guiGraphics.guiWidth();
        float h = guiGraphics.guiHeight();
        boolean outroActive = splash.outro.isStarted();
        if (outroActive) {
            // Resources are consistent again: the live screen (and world, if any) shows through the letters.
            if (minecraft.screen != null) {
                ClientHooks.drawScreen(minecraft.screen, guiGraphics, mouseX, mouseY, partialTick);
            }
            guiGraphics.flush();
        } else {
            drawFrozen(guiGraphics, w, h);
        }

        Matrix4f pose = guiGraphics.pose().last().pose();
        int background = MojangSplash.brandColor(minecraft);
        IntroDraw.begin();
        if (outroActive) {
            splash.drawOutro(pose, w, h, background, progress);
        } else {
            float in = fadeIn ? Easing.OUT_CUBIC.apply(Mth.clamp(t / FADE_IN_SECONDS, 0.0F, 1.0F)) : 1.0F;
            splash.drawLoading(pose, w, h, background, progress, in);
        }
        IntroDraw.end();

        if (splash.outro.isFinished()) {
            release();
            remove();
        }
    }

    private void freeze(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        frozenTaken = true;
        // Like vanilla's fade-in, the screen is drawn once more; the world and HUD (if any) are already in the frame.
        if (fadeIn && minecraft.screen != null) {
            ClientHooks.drawScreen(minecraft.screen, guiGraphics, mouseX, mouseY, partialTick);
        }
        guiGraphics.flush();
        frozen = snapshot();
    }

    @Nullable
    private TextureTarget snapshot() {
        try {
            RenderTarget main = minecraft.getMainRenderTarget();
            TextureTarget target = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            copy(main, target, main);
            return target;
        } catch (RuntimeException e) {
            LiquidGlass.LOGGER.warn("Could not freeze the screen for the reload overlay", e);
            return null;
        }
    }

    private void drawFrozen(GuiGraphics guiGraphics, float w, float h) {
        RenderTarget main = minecraft.getMainRenderTarget();
        if (frozen != null && frozen.width == main.width && frozen.height == main.height) {
            guiGraphics.flush();
            copy(frozen, main, main);
        } else {
            IntroDraw.begin();
            IntroDraw.fill(guiGraphics.pose().last().pose(), 0.0F, 0.0F, w, h, FREEZE_FALLBACK);
            IntroDraw.end();
        }
    }

    private static void copy(RenderTarget from, RenderTarget to, RenderTarget main) {
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GlStateManager._disableScissorTest();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);
        if (scissor) {
            GlStateManager._enableScissorTest();
        }
    }

    private void release() {
        released = true;
        splash.close();
        if (frozen != null) {
            frozen.destroyBuffers();
            frozen = null;
        }
    }
}
