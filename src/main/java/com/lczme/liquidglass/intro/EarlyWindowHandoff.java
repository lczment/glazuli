package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.LiquidGlass;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.neoforged.fml.earlydisplay.ColourScheme;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.earlydisplay.RenderElement;
import org.joml.Matrix4f;

/**
 * Shows the last frame the FML early loading window painted, placed exactly like {@code NeoForgeLoadingOverlay}
 * does, so the first intro frame is identical to what was on screen before and the intro can dissolve over it.
 * <p>
 * The early window is not asked to repaint ({@link DisplayWindow#render(int)} changes GL state behind
 * {@code GlStateManager}'s caches); its framebuffer texture still holds the final early frame.
 */
final class EarlyWindowHandoff {
    private final DisplayWindow window;
    private final int background;
    private boolean closed;

    EarlyWindowHandoff(DisplayWindow window) {
        this.window = window;
        ColourScheme.Colour c = window.context().colourScheme().background();
        this.background = FastColor.ARGB32.color(255, c.red(), c.green(), c.blue());
    }

    int background() {
        return background;
    }

    /** Draws the early frame over the whole screen, faded to {@code alpha}. */
    void draw(Matrix4f pose, float guiWidth, float guiHeight, float alpha) {
        if (closed) {
            return;
        }
        Window mcWindow = Minecraft.getInstance().getWindow();
        float fbWidth = mcWindow.getWidth();
        float fbHeight = mcWindow.getHeight();
        RenderElement.DisplayContext ctx = window.context();
        float scale = ctx.scale() * Math.min(fbWidth / ctx.width(), fbHeight / ctx.height()) / 2.0F;
        float gs = (float) mcWindow.getGuiScale();
        float left = Mth.clamp(fbWidth * 0.5F - scale * ctx.width(), 0.0F, fbWidth) / gs;
        float right = Mth.clamp(fbWidth * 0.5F + scale * ctx.width(), 0.0F, fbWidth) / gs;
        float top = Mth.clamp(fbHeight * 0.5F - scale * ctx.height(), 0.0F, fbHeight) / gs;
        float bottom = Mth.clamp(fbHeight * 0.5F + scale * ctx.height(), 0.0F, fbHeight) / gs;

        IntroDraw.begin();
        IntroDraw.fill(pose, 0.0F, 0.0F, guiWidth, guiHeight, IntroDraw.withAlpha(background, alpha));
        IntroDraw.blendPremultiplied();
        IntroDraw.texture(pose, window.getFramebufferTextureId(), left, top, right - left, bottom - top,
                IntroDraw.withAlpha(0xFFFFFFFF, alpha));
        IntroDraw.end();
    }

    /** Frees the early window's GL resources, as {@code NeoForgeLoadingOverlay} does when it finishes. */
    void close() {
        if (!closed) {
            closed = true;
            try {
                window.close();
            } catch (Throwable t) {
                LiquidGlass.LOGGER.warn("Failed to close the early loading window", t);
            }
        }
    }
}
