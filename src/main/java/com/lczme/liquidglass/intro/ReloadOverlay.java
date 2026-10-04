package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.render.GlassRenderer;
import com.lczme.liquidglass.render.GlassStyle;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Lighter overlay for resource reloads after startup: the current frame is frozen and frosted, with a small glass
 * card showing progress. Freezing matters because textures, models and fonts are swapped while the reload runs, so
 * live screen/world content is not safe to show (vanilla covers it completely for the same reason).
 */
public final class ReloadOverlay extends GlassLoadingOverlay {
    private static final String LABEL_KEY = "liquidglass.loading.reloading";
    private static final float FADE_IN_SECONDS = 0.3F;
    /** Vanilla keeps a fading-in overlay up for at least one second; ours is snappier but still avoids a flash. */
    private static final float MIN_SECONDS_WITH_FADE_IN = 0.8F;
    private static final float FADE_OUT_SECONDS = 0.45F;
    private static final int FREEZE_FALLBACK = 0xFF0B0D12;
    private static final GlassStyle FROST = GlassStyle.film(0x0B0D12, GlassStyle.TINT_NEUTRAL).withCornerRadius(0.0F).withShadow(0.0F);
    private static final GlassStyle CARD = GlassStyle.FILM;

    private final String label;
    @Nullable
    private TextureTarget frozen;
    private boolean frozenTaken;
    private double fadeOutStart = Double.NaN;
    private boolean released;

    ReloadOverlay(Minecraft minecraft, ReloadInstance reload, Consumer<Optional<Throwable>> onFinish, boolean fadeIn) {
        super(minecraft, reload, onFinish, fadeIn);
        String text = I18n.get(LABEL_KEY);
        this.label = text.equals(LABEL_KEY) ? "Reloading resources" : text;
        bar.setLabel(label);
    }

    /** Seconds since the reload finished and the frost started thawing, or a negative value before that. */
    public float fadeOutElapsed() {
        return Double.isNaN(fadeOutStart) ? -1.0F : (float) (AnimClock.now() - fadeOutStart);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (released) {
            return;
        }
        markFrame();
        float t = elapsed();
        bar.update(reload.getActualProgress(), reload.isDone());
        if (!frozenTaken) {
            freeze(guiGraphics, mouseX, mouseY, partialTick);
        }

        if (!isFinishCalled() && reload.isDone() && (!fadeIn || t >= MIN_SECONDS_WITH_FADE_IN)) {
            finishReload(guiGraphics);
            fadeOutStart = AnimClock.now();
            if (isReplaced()) {
                release();
                return;
            }
        }

        float in = fadeIn ? Easing.OUT_CUBIC.apply(Mth.clamp(t / FADE_IN_SECONDS, 0.0F, 1.0F)) : 1.0F;
        float out = Double.isNaN(fadeOutStart) ? 0.0F
                : Easing.IN_OUT_CUBIC.apply(Mth.clamp((float) (AnimClock.now() - fadeOutStart) / FADE_OUT_SECONDS, 0.0F, 1.0F));
        float w = guiGraphics.guiWidth();
        float h = guiGraphics.guiHeight();

        if (isFinishCalled()) {
            // Resources are consistent again: show the live screen under the thawing frost.
            if (minecraft.screen != null) {
                ClientHooks.drawScreen(minecraft.screen, guiGraphics, mouseX, mouseY, partialTick);
            }
            guiGraphics.flush();
        } else {
            drawFrozen(guiGraphics, w, h);
        }

        float frost = in * (1.0F - out);
        if (frost > 0.0F) {
            GlassRenderer.recaptureBackdrop(guiGraphics);
            GlassRenderer.drawPanel(guiGraphics, -16.0F, -16.0F, w + 32.0F, h + 32.0F, FROST.withOpacity(frost));
        }
        drawCard(guiGraphics, w, h, in, out);

        if (out >= 1.0F) {
            release();
            remove();
        }
    }

    private void drawCard(GuiGraphics guiGraphics, float w, float h, float in, float out) {
        float alpha = in * (1.0F - out);
        if (alpha <= 0.0F) {
            return;
        }
        float cardW = Mth.clamp(w * 0.36F, 160.0F, 240.0F);
        float cardH = 58.0F;
        float cx = w * 0.5F;
        float cy = h * 0.5F;
        float scale = Mth.lerp(in, 0.94F, 1.0F) * Mth.lerp(out, 1.0F, 0.96F);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(cx, cy, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        GlassRenderer.drawPanel(guiGraphics, -cardW * 0.5F, -cardH * 0.5F, cardW, cardH, CARD.withOpacity(alpha));
        Matrix4f pose = guiGraphics.pose().last().pose();
        float logoHeight = 15.0F;
        IntroLogo.prepare(logoHeight);
        IntroLogo.draw(pose, 0.0F, -cardH * 0.5F + 16.0F, logoHeight, alpha, 2.0F);
        bar.draw(pose, 0.0F, 4.0F, cardW - 40.0F, alpha);
        guiGraphics.pose().popPose();
    }

    private void freeze(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        frozenTaken = true;
        // Like vanilla's fade-in, the screen is drawn once more; the world and HUD (if any) are already in the frame.
        if (fadeIn && minecraft.screen != null) {
            ClientHooks.drawScreen(minecraft.screen, guiGraphics, mouseX, mouseY, partialTick);
        }
        guiGraphics.flush();
        try {
            RenderTarget main = minecraft.getMainRenderTarget();
            frozen = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            copy(main, frozen, main);
        } catch (RuntimeException e) {
            LiquidGlass.LOGGER.warn("Could not freeze the screen for the reload overlay", e);
            frozen = null;
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
        if (frozen != null) {
            frozen.destroyBuffers();
            frozen = null;
        }
    }
}
