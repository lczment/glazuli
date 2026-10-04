package com.lczme.liquidglass.render;

import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.anim.Easing;
import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Cross-fade between menus: on a screen switch the last presented frame is copied and drawn over the new screen,
 * fading out with the spec's {@code --ease}. Only screen-to-screen switches fade; opening or closing a menu over the
 * world would freeze the world image for the duration.
 */
public final class ScreenFade {
    private static final float DURATION = 0.3F;

    @Nullable
    private static TextureTarget snapshot;
    private static double startTime = Double.NaN;
    // Between RenderFrameEvent.Pre and Post the main target holds a partial frame, so it can't be snapshotted.
    private static boolean inFrame;

    private ScreenFade() {
    }

    public static void onSetScreen(Minecraft minecraft, @Nullable Screen next) {
        Screen current = minecraft.screen;
        if (current == null || next == null || current == next || inFrame || minecraft.getOverlay() != null
                || !RenderSystem.isOnRenderThread() || !isEnabled()) {
            return;
        }
        copyMainTarget(minecraft.getMainRenderTarget());
        startTime = AnimClock.now();
    }

    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        inFrame = true;
    }

    public static void onRenderFramePost(RenderFrameEvent.Post event) {
        inFrame = false;
        if (Double.isNaN(startTime) || snapshot == null) {
            return;
        }
        float t = (float) ((AnimClock.now() - startTime) / DURATION);
        ShaderInstance shader = LiquidGlassShaders.screenFade();
        if (t >= 1.0F || shader == null) {
            startTime = Double.NaN;
            return;
        }
        drawSnapshot(shader, 1.0F - Easing.SPEC_EASE.apply(Math.max(0.0F, t)));
    }

    private static boolean isEnabled() {
        return LiquidGlassConfig.SPEC.isLoaded() && LiquidGlassConfig.SCREEN_FADE.get();
    }

    private static void copyMainTarget(RenderTarget main) {
        if (snapshot == null || snapshot.width != main.width || snapshot.height != main.height) {
            if (snapshot != null) {
                snapshot.destroyBuffers();
            }
            snapshot = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            snapshot.setFilterMode(GL11.GL_LINEAR);
        }
        int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GlStateManager._disableScissorTest();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        if (scissor) {
            GlStateManager._enableScissorTest();
        }
    }

    private static void drawSnapshot(ShaderInstance shader, float alpha) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        main.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        shader.setSampler("DiffuseSampler", snapshot.getColorTextureId());
        shader.safeGetUniform("Alpha").set(alpha);
        shader.apply();
        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
        builder.addVertex(0.0F, 0.0F, 0.0F);
        builder.addVertex(1.0F, 0.0F, 0.0F);
        builder.addVertex(1.0F, 1.0F, 0.0F);
        builder.addVertex(0.0F, 1.0F, 0.0F);
        BufferUploader.draw(builder.buildOrThrow());
        shader.clear();
        RenderSystem.disableBlend();
        if (depth) {
            RenderSystem.enableDepthTest();
        }
    }
}
