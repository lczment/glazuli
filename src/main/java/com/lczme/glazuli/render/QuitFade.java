package com.lczme.glazuli.render;

import com.lczme.glazuli.anim.AnimClock;
import com.lczme.glazuli.anim.Easing;
import com.lczme.glazuli.config.GlazuliConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Quitting fades the game to black before the window closes: {@code Minecraft.stop()} is held back until the fade has
 * presented a fully black frame. Black stays on top afterwards, covering the world-saving screen during shutdown.
 */
public final class QuitFade {
    private static final float DURATION = 0.45F;
    /** If frames stop coming, quitting must never hang on the fade. */
    private static final float GIVE_UP = DURATION + 1.5F;

    private static double startTime = Double.NaN;
    private static boolean finished;

    private QuitFade() {
    }

    /** Returns true if the stop request should wait for the fade, which then repeats it. */
    public static boolean deferStop() {
        if (finished || !RenderSystem.isOnRenderThread() || !isEnabled()) {
            return false;
        }
        if (Double.isNaN(startTime)) {
            startTime = AnimClock.now();
        }
        return AnimClock.now() - startTime < GIVE_UP;
    }

    public static void onRenderFramePost(RenderFrameEvent.Post event) {
        if (Double.isNaN(startTime)) {
            return;
        }
        float t = Mth.clamp((float) ((AnimClock.now() - startTime) / DURATION), 0.0F, 1.0F);
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics guiGraphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        int alpha = Math.round(Easing.SPEC_EASE.apply(t) * 255.0F);
        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), FastColor.ARGB32.color(alpha, 0));
        guiGraphics.flush();
        if (t >= 1.0F && !finished) {
            finished = true;
            minecraft.stop();
        }
    }

    private static boolean isEnabled() {
        return GlazuliConfig.SPEC.isLoaded() && GlazuliConfig.SCREEN_FADE.get() && !GlazuliConfig.isClassic();
    }
}
