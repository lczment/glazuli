package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.anim.Spring;
import com.lczme.liquidglass.render.GlassButtons;
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

/** Swaps only the background sprite, so labels and subclass decorations (e.g. icon buttons) still render as usual. */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
    @Unique
    private Spring liquidglass$press;

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void liquidglass$drawGlassBackground(GuiGraphics guiGraphics, ResourceLocation sprite, int x, int y, int width, int height,
                                                 Operation<Void> original) {
        if (!GlassButtons.isEnabled()) {
            original.call(guiGraphics, sprite, x, y, width, height);
            return;
        }
        if (liquidglass$press == null) {
            liquidglass$press = Spring.of(GlassButtons.PRESS_RESPONSE, GlassButtons.PRESS_DAMPING, 1.0F);
        }
        liquidglass$press.setTarget(liquidglass$isPressed() ? GlassButtons.PRESSED_SCALE : 1.0F);
        GlassButtons.draw(guiGraphics, x, y, width, height, liquidglass$press.value(),
                GlassButtons.styleFor((AbstractWidget) (Object) this));
    }

    /** Held by the mouse while hovered, or by Enter/Space while keyboard-focused; released as soon as either ends. */
    @Unique
    private boolean liquidglass$isPressed() {
        AbstractWidget self = (AbstractWidget) (Object) this;
        if (!self.active) {
            return false;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (self.isHovered() && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
            return true;
        }
        return self.isFocused() && (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_ENTER)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_KP_ENTER));
    }
}
