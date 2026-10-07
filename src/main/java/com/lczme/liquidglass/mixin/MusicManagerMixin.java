package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.intro.StartupSound;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    /** No menu music during loading and the startup sting; {@link StartupSound} starts it afterwards. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void liquidglass$holdForStartupSound(CallbackInfo ci) {
        if (StartupSound.holdsMusic()) {
            ci.cancel();
        }
    }
}
