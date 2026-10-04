package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.intro.IntroOverlays;
import com.lczme.liquidglass.render.ScreenFade;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "setOverlay", at = @At("HEAD"), argsOnly = true)
    @Nullable
    private Overlay liquidglass$replaceLoadingOverlay(@Nullable Overlay overlay) {
        return IntroOverlays.replace((Minecraft) (Object) this, overlay);
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void liquidglass$snapshotForFade(@Nullable Screen screen, CallbackInfo ci) {
        ScreenFade.onSetScreen((Minecraft) (Object) this, screen);
    }
}
