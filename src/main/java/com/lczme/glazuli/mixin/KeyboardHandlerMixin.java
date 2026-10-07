package com.lczme.glazuli.mixin;

import com.lczme.glazuli.intro.IntroState;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    /** Any key skips the intro outro; the press is consumed so it doesn't also act on the screen behind it. */
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void glazuli$skipIntro(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && IntroState.requestSkip()) {
            ci.cancel();
        }
    }
}
