package com.lczme.glazuli.mixin;

import com.lczme.glazuli.anim.AnimClock;
import com.lczme.glazuli.anim.Spring;
import com.lczme.glazuli.render.GlassButtons;
import com.lczme.glazuli.render.GlassPressable;
import com.lczme.glazuli.render.GlassRenderer;
import com.lczme.glazuli.render.GlassStyle;
import com.lczme.glazuli.render.PackOverrides;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps only the background sprite, so labels and subclass decorations (e.g. icon buttons) still render as usual.
 * A sprite supplied by a player resource pack is drawn as is; the press and hover animations still apply.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin implements GlassPressable {
    // A deferred click is only trusted while the button keeps being drawn; otherwise it would fire later, out of context.
    @Unique
    private static final double GLAZULI$STALE_SECONDS = 0.2;

    @Unique
    private Spring glazuli$press;
    @Unique
    private boolean glazuli$pendingClick;
    @Unique
    private double glazuli$lastFrame = Double.NEGATIVE_INFINITY;
    @Unique
    private double glazuli$releasedAt = Double.NaN;
    @Unique
    private Spring glazuli$hoverSpring;
    @Unique
    private float glazuli$hoverValue;

    @Override
    public float glazuli$hover() {
        return glazuli$hoverValue;
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void glazuli$drawGlassBackground(GuiGraphics guiGraphics, ResourceLocation sprite, int x, int y, int width, int height,
                                                 Operation<Void> original) {
        if (!GlassButtons.isEnabled() || PackOverrides.sprite(sprite)) {
            original.call(guiGraphics, sprite, x, y, width, height);
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        GlassStyle style = GlassButtons.styleFor(self).withHover(glazuli$hover());
        // Fading widgets (title entrance, page turns) fade their glass with the label.
        float alpha = ((AbstractWidgetAccessor) self).glazuli$alpha();
        if (alpha < 1.0F) {
            style = style.withOpacity(style.opacity() * Math.max(alpha, 0.0F));
        }
        GlassRenderer.drawPanel(guiGraphics, x, y, width, height, style);
    }

    /** Mouse clicks act on release (see {@link #glazuli$pressScale}); keyboard and programmatic presses are untouched. */
    @Inject(method = "onClick(DD)V", at = @At("HEAD"), cancellable = true)
    private void glazuli$deferClick(double mouseX, double mouseY, CallbackInfo ci) {
        if (GlassButtons.isEnabled() && glazuli$mouseHeld()
                && AnimClock.now() - glazuli$lastFrame < GLAZULI$STALE_SECONDS) {
            glazuli$pendingClick = true;
            ci.cancel();
        }
    }

    @Override
    public float glazuli$pressScale() {
        AbstractButton self = (AbstractButton) (Object) this;
        double now = AnimClock.now();
        boolean held = glazuli$mouseHeld();
        boolean stale = now - glazuli$lastFrame >= GLAZULI$STALE_SECONDS;
        if (glazuli$pendingClick && (stale || !held)) {
            glazuli$pendingClick = false;
            if (!stale && self.isHovered() && self.active && self.visible) {
                glazuli$releasedAt = now;
            }
        }
        if (!Double.isNaN(glazuli$releasedAt)) {
            if (stale) {
                glazuli$releasedAt = Double.NaN;
            } else if (now - glazuli$releasedAt >= GlassButtons.ACTION_DELAY) {
                glazuli$releasedAt = Double.NaN;
                // Run outside the render pass: onPress usually switches screens.
                Minecraft.getInstance().tell(self::onPress);
            }
        }
        glazuli$lastFrame = now;

        if (glazuli$press == null) {
            glazuli$press = Spring.of(GlassButtons.PRESS_RESPONSE, GlassButtons.PRESS_DAMPING, 1.0F);
            glazuli$hoverSpring = Spring.of(GlassButtons.HOVER_RESPONSE, GlassButtons.HOVER_DAMPING, 0.0F);
        }
        glazuli$hoverSpring.setTarget(self.active && self.isHovered());
        glazuli$hoverValue = Math.max(0.0F, glazuli$hoverSpring.value());
        if (glazuli$isPressed()) {
            glazuli$press.retune(GlassButtons.PRESS_RESPONSE, GlassButtons.PRESS_DAMPING);
            glazuli$press.setTarget(GlassButtons.PRESSED_SCALE);
        } else {
            glazuli$press.retune(GlassButtons.RELEASE_RESPONSE, GlassButtons.RELEASE_DAMPING);
            glazuli$press.setTarget(1.0F);
        }
        return glazuli$press.value();
    }

    /** Held from mouse-down until release (even if dragged off, like a Web button), or by Enter/Space while focused. */
    @Unique
    private boolean glazuli$isPressed() {
        AbstractWidget self = (AbstractWidget) (Object) this;
        if (!self.active) {
            return false;
        }
        if (glazuli$pendingClick) {
            return true;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        return self.isFocused() && (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_ENTER)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_KP_ENTER));
    }

    @Unique
    private static boolean glazuli$mouseHeld() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }
}
