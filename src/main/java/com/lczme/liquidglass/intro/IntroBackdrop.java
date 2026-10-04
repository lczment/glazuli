package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.anim.AnimClock;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Dark animated "aurora" background of the intro.
 * <p>
 * Our core shaders are normally registered through {@code RegisterShadersEvent}, which only completes at the end of
 * the initial resource reload. This one is compiled directly from the resource manager instead, which already holds
 * every pack (including the mod's) as soon as the reload starts. If it fails, a plain gradient is drawn.
 */
public final class IntroBackdrop {
    private static final int TOP = 0xFF141824;
    private static final int BOTTOM = 0xFF0B0D12;

    @Nullable
    private static ShaderInstance shader;
    private static boolean failed;

    private IntroBackdrop() {
    }

    /**
     * Draws the backdrop over the whole screen.
     *
     * @param glowX     glow center in GUI coordinates
     * @param glowY     glow center in GUI coordinates
     * @param glowRadius glow radius in GUI units
     * @param glow      glow intensity, 0 for none
     */
    public static void draw(Matrix4f pose, float width, float height, float alpha, float glowX, float glowY, float glowRadius, float glow) {
        if (alpha <= 0.0F) {
            return;
        }
        ShaderInstance program = shader();
        IntroDraw.begin();
        if (program == null) {
            drawFallback(pose, width, height, alpha, glowX, glowY, glowRadius, glow);
            IntroDraw.end();
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        float scale = (float) window.getGuiScale();
        program.safeGetUniform("Resolution").set((float) window.getWidth(), (float) window.getHeight());
        program.safeGetUniform("Time").set((float) (AnimClock.now() % 3600.0));
        program.safeGetUniform("Alpha").set(Mth.clamp(alpha, 0.0F, 1.0F));
        program.safeGetUniform("Glow").set(glowX * scale, window.getHeight() - glowY * scale, glowRadius * scale, glow);
        RenderSystem.setShader(() -> program);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.addVertex(pose, 0.0F, 0.0F, 0.0F);
        b.addVertex(pose, 0.0F, height, 0.0F);
        b.addVertex(pose, width, height, 0.0F);
        b.addVertex(pose, width, 0.0F, 0.0F);
        BufferUploader.drawWithShader(b.buildOrThrow());
        IntroDraw.end();
    }

    private static void drawFallback(Matrix4f pose, float width, float height, float alpha, float glowX, float glowY, float glowRadius, float glow) {
        IntroDraw.gradient(pose, 0.0F, 0.0F, width, height, IntroDraw.withAlpha(TOP, alpha), IntroDraw.withAlpha(BOTTOM, alpha));
        IntroDraw.blendAdditive();
        IntroDraw.glow(pose, width * 0.22F, height * 0.3F, width * 0.4F, height * 0.35F, IntroDraw.withAlpha(0xFF1A2A5C, alpha * 0.8F));
        IntroDraw.glow(pose, width * 0.8F, height * 0.25F, width * 0.35F, height * 0.3F, IntroDraw.withAlpha(0xFF2E1E52, alpha * 0.8F));
        IntroDraw.glow(pose, glowX, glowY, glowRadius, glowRadius, IntroDraw.withAlpha(0xFF4D73F2, alpha * glow));
        IntroDraw.blendNormal();
    }

    @Nullable
    private static ShaderInstance shader() {
        if (shader == null && !failed) {
            try {
                shader = new ShaderInstance(Minecraft.getInstance().getResourceManager(), LiquidGlass.id("intro_backdrop"), DefaultVertexFormat.POSITION);
            } catch (Exception e) {
                failed = true;
                LiquidGlass.LOGGER.error("Failed to load the intro backdrop shader, using a flat gradient", e);
            }
        }
        return shader;
    }

    /** Frees the shader; it is recompiled lazily if drawn again. Render thread. */
    public static void close() {
        if (shader != null) {
            shader.close();
            shader = null;
        }
    }
}
