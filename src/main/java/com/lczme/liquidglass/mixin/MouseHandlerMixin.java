package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.intro.IntroState;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void liquidglass$skipIntro(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && IntroState.requestSkip()) {
            ci.cancel();
        }
    }
}
