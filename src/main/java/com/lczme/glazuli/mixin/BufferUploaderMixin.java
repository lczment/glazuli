package com.lczme.glazuli.mixin;

import com.lczme.glazuli.dev.Perf;
import com.mojang.blaze3d.vertex.BufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draw call count for the benchmark. */
@Mixin(BufferUploader.class)
public abstract class BufferUploaderMixin {
    @Inject(method = {"draw", "drawWithShader"}, at = @At("HEAD"))
    private static void glazuli$countDraw(CallbackInfo ci) {
        Perf.noteDraw();
    }
}
