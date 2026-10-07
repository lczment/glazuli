package com.lczme.glazuli.render;

import com.lczme.glazuli.config.GlazuliConfig;
import com.lczme.glazuli.dev.Perf;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Captures the main framebuffer and produces a dual-Kawase blurred copy of it at half resolution.
 * <p>
 * The capture is cached per frame: the first glass draw of a frame captures lazily, later draws reuse it.
 * Call {@link #capture(GuiGraphics)} directly to re-capture mid-frame (e.g. a popup that should blur the
 * panels already drawn beneath it).
 */
public final class BlurPipeline {
    private static final float KAWASE_OFFSET = 1.5F;

    @Nullable
    private static TextureTarget sharp;
    @Nullable
    private static TextureTarget blurred;
    private static final List<TextureTarget> levels = new ArrayList<>();
    private static int sourceWidth = -1;
    private static int sourceHeight = -1;

    private static long frame;
    private static long capturedFrame = -1;
    private static boolean captureBlurred;

    private BlurPipeline() {
    }

    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        frame++;
    }

    /** A screen draws over the HUD (dimmed or blurred), so its panels must not reuse a capture taken by HUD glass. */
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        capturedFrame = -1;
    }

    /** Captures the backdrop unless it was already captured this frame. */
    public static void ensureCaptured(GuiGraphics guiGraphics) {
        if (capturedFrame != frame) {
            capture(guiGraphics);
        }
    }

    /** Captures the current main framebuffer contents now, including everything already submitted to {@code guiGraphics}. */
    public static void capture(GuiGraphics guiGraphics) {
        RenderSystem.assertOnRenderThread();
        // GuiGraphics batches draws; without the flush, already "drawn" GUI would be missing from the capture.
        guiGraphics.flush();
        Perf.noteCapture();
        Perf.begin(Perf.CAPTURE);
        captureFlushed();
        Perf.end(Perf.CAPTURE);
    }

    private static void captureFlushed() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        // Usually the main target, but screens may draw into their own (Catnip's do, for stencils).
        int screenTarget = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int passes = GlazuliConfig.BLUR_QUALITY.get().passes();
        ShaderInstance down = GlazuliShaders.kawaseDown();
        ShaderInstance up = GlazuliShaders.kawaseUp();

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        ensureTargets(main.width, main.height, passes);

        // glBlitFramebuffer and our passes are both clipped by the scissor test; turning it off keeps the box.
        GlStateManager._disableScissorTest();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, screenTarget);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sharp.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, sharp.width, sharp.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

        captureBlurred = passes > 0 && down != null && up != null;
        if (captureBlurred) {
            RenderTarget src = sharp;
            for (int i = 0; i < passes; i++) {
                RenderTarget dst = levels.get(i);
                runPass(down, src, dst);
                src = dst;
            }
            for (int i = passes - 1; i > 0; i--) {
                runPass(up, levels.get(i), levels.get(i - 1));
            }
            runPass(up, levels.get(0), blurred);
        }

        main.bindWrite(true);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, screenTarget);
        if (scissor) {
            GlStateManager._enableScissorTest();
        }
        if (depth) {
            RenderSystem.enableDepthTest();
        }
        if (blend) {
            RenderSystem.enableBlend();
        }

        capturedFrame = frame;
    }

    /** Texture id of the blurred backdrop, or the sharp copy if blurring is unavailable. Valid after a capture. */
    public static int blurredTexture() {
        return captureBlurred && blurred != null ? blurred.getColorTextureId() : sharpTexture();
    }

    /** Texture id of the unblurred (half resolution) backdrop. Valid after a capture. */
    public static int sharpTexture() {
        return sharp != null ? sharp.getColorTextureId() : -1;
    }

    private static void runPass(ShaderInstance shader, RenderTarget src, RenderTarget dst) {
        dst.bindWrite(true);
        shader.setSampler("DiffuseSampler", src.getColorTextureId());
        shader.safeGetUniform("HalfPixel").set(0.5F / dst.width, 0.5F / dst.height);
        shader.safeGetUniform("Offset").set(KAWASE_OFFSET);
        shader.apply();
        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
        builder.addVertex(0.0F, 0.0F, 0.0F);
        builder.addVertex(1.0F, 0.0F, 0.0F);
        builder.addVertex(1.0F, 1.0F, 0.0F);
        builder.addVertex(0.0F, 1.0F, 0.0F);
        BufferUploader.draw(builder.buildOrThrow());
        shader.clear();
    }

    private static void ensureTargets(int width, int height, int passes) {
        if (width == sourceWidth && height == sourceHeight && levels.size() == passes && sharp != null) {
            return;
        }
        destroy();
        sourceWidth = width;
        sourceHeight = height;
        int halfW = Math.max(1, width / 2);
        int halfH = Math.max(1, height / 2);
        sharp = createTarget(halfW, halfH);
        blurred = createTarget(halfW, halfH);
        for (int i = 0; i < passes; i++) {
            levels.add(createTarget(Math.max(1, width >> (i + 2)), Math.max(1, height >> (i + 2))));
        }
    }

    private static TextureTarget createTarget(int width, int height) {
        TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        target.setFilterMode(GL11.GL_LINEAR);
        return target;
    }

    /** Frees all GPU resources. They are recreated on the next capture. */
    public static void destroy() {
        RenderSystem.assertOnRenderThreadOrInit();
        if (sharp != null) {
            sharp.destroyBuffers();
            sharp = null;
        }
        if (blurred != null) {
            blurred.destroyBuffers();
            blurred = null;
        }
        levels.forEach(RenderTarget::destroyBuffers);
        levels.clear();
        sourceWidth = -1;
        sourceHeight = -1;
        capturedFrame = -1;
    }
}
