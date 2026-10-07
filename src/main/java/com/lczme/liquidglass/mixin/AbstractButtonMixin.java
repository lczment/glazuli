package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.anim.Spring;
import com.lczme.liquidglass.render.GlassButtons;
import com.lczme.liquidglass.render.GlassPressable;
import com.lczme.liquidglass.render.GlassRenderer;
import com.lczme.liquidglass.render.GlassStyle;
import com.lczme.liquidglass.render.PackOverrides;
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
    private static final double LIQUIDGLASS$STALE_SECONDS = 0.2;

    @Unique
    private Spring liquidglass$press;
    @Unique
    private boolean liquidglass$pendingClick;
    @Unique
    private double liquidglass$lastFrame = Double.NEGATIVE_INFINITY;
    @Unique
    private double liquidglass$releasedAt = Double.NaN;
    @Unique
    private Spring liquidglass$hoverSpring;
    @Unique
    private float liquidglass$hoverValue;

    @Override
    public float liquidglass$hover() {
        return liquidglass$hoverValue;
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void liquidglass$drawGlassBackground(GuiGraphics guiGraphics, ResourceLocation sprite, int x, int y, int width, int height,
                                                 Operation<Void> original) {
        if (!GlassButtons.isEnabled() || PackOverrides.sprite(sprite)) {
            original.call(guiGraphics, sprite, x, y, width, height);
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        GlassStyle style = GlassButtons.styleFor(self).withHover(liquidglass$hover());
        // Fading widgets (title entrance, page turns) fade their glass with the label.
        float alpha = ((AbstractWidgetAccessor) self).liquidglass$alpha();
        if (alpha < 1.0F) {
            style = style.withOpacity(style.opacity() * Math.max(alpha, 0.0F));
        }
        GlassRenderer.drawPanel(guiGraphics, x, y, width, height, style);
    }

    /** Mouse clicks act on release (see {@link #liquidglass$pressScale}); keyboard and programmatic presses are untouched. */
    @Inject(method = "onClick(DD)V", at = @At("HEAD"), cancellable = true)
    private void liquidglass$deferClick(double mouseX, double mouseY, CallbackInfo ci) {
        if (GlassButtons.isEnabled() && liquidglass$mouseHeld()
                && AnimClock.now() - liquidglass$lastFrame < LIQUIDGLASS$STALE_SECONDS) {
            liquidglass$pendingClick = true;
            ci.cancel();
        }
    }

    @Override
    public float liquidglass$pressScale() {
        AbstractButton self = (AbstractButton) (Object) this;
        double now = AnimClock.now();
        boolean held = liquidglass$mouseHeld();
        boolean stale = now - liquidglass$lastFrame >= LIQUIDGLASS$STALE_SECONDS;
        if (liquidglass$pendingClick && (stale || !held)) {
            liquidglass$pendingClick = false;
            if (!stale && self.isHovered() && self.active && self.visible) {
                liquidglass$releasedAt = now;
            }
        }
        if (!Double.isNaN(liquidglass$releasedAt)) {
            if (stale) {
                liquidglass$releasedAt = Double.NaN;
            } else if (now - liquidglass$releasedAt >= GlassButtons.ACTION_DELAY) {
                liquidglass$releasedAt = Double.NaN;
                // Run outside the render pass: onPress usually switches screens.
                Minecraft.getInstance().tell(self::onPress);
            }
        }
        liquidglass$lastFrame = now;

        if (liquidglass$press == null) {
            liquidglass$press = Spring.of(GlassButtons.PRESS_RESPONSE, GlassButtons.PRESS_DAMPING, 1.0F);
            liquidglass$hoverSpring = Spring.of(GlassButtons.HOVER_RESPONSE, GlassButtons.HOVER_DAMPING, 0.0F);
        }
        liquidglass$hoverSpring.setTarget(self.active && self.isHovered());
        liquidglass$hoverValue = Math.max(0.0F, liquidglass$hoverSpring.value());
        if (liquidglass$isPressed()) {
            liquidglass$press.retune(GlassButtons.PRESS_RESPONSE, GlassButtons.PRESS_DAMPING);
            liquidglass$press.setTarget(GlassButtons.PRESSED_SCALE);
        } else {
            liquidglass$press.retune(GlassButtons.RELEASE_RESPONSE, GlassButtons.RELEASE_DAMPING);
            liquidglass$press.setTarget(1.0F);
        }
        return liquidglass$press.value();
    }

    /** Held from mouse-down until release (even if dragged off, like a Web button), or by Enter/Space while focused. */
    @Unique
    private boolean liquidglass$isPressed() {
        AbstractWidget self = (AbstractWidget) (Object) this;
        if (!self.active) {
            return false;
        }
        if (liquidglass$pendingClick) {
            return true;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        return self.isFocused() && (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_ENTER)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_KP_ENTER));
    }

    @Unique
    private static boolean liquidglass$mouseHeld() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }
}
