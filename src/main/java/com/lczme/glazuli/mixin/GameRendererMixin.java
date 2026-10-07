package com.lczme.glazuli.mixin;

import com.lczme.glazuli.render.BlurFade;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyArg(method = "processBlurEffect", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/PostChain;setUniform(Ljava/lang/String;F)V"), index = 1)
    private float glazuli$easedRadius(float radius) {
        return BlurFade.radius(radius);
    }
}
