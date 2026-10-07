package com.lczme.glazuli.mixin;

import com.lczme.glazuli.cave.CaveBackground;
import com.lczme.glazuli.font.FontScale;
import com.lczme.glazuli.intro.IntroOverlays;
import com.lczme.glazuli.intro.IntroState;
import com.lczme.glazuli.render.QuitFade;
import com.lczme.glazuli.render.ScreenFade;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "setOverlay", at = @At("HEAD"), argsOnly = true)
    @Nullable
    private Overlay glazuli$replaceLoadingOverlay(@Nullable Overlay overlay) {
        return IntroOverlays.replace((Minecraft) (Object) this, overlay);
    }

    @Inject(method = "resizeDisplay", at = @At("TAIL"))
    private void glazuli$refitFont(CallbackInfo ci) {
        FontScale.onResize();
    }

    @Inject(method = "stop", at = @At("HEAD"), cancellable = true)
    private void glazuli$fadeOutOnQuit(CallbackInfo ci) {
        if (QuitFade.deferStop()) {
            ci.cancel();
        }
    }

    /**
     * Vanilla caps menus and loading screens at 60 fps; on a faster display that judders the animated startup and the
     * cave camera, so they get the player's own limit.
     */
    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void glazuli$uncapAnimatedMenus(CallbackInfoReturnable<Integer> cir) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.level != null) {
            return;
        }
        // Called every frame from the start: CaveBackground may only be touched once mod loading is over, as its
        // dependencies (blocks, render types) must not be class-loaded while mods register.
        if (IntroState.isPlaying() || IntroState.isDone() && minecraft.getOverlay() == null && CaveBackground.isActive()) {
            cir.setReturnValue(minecraft.getWindow().getFramerateLimit());
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void glazuli$snapshotForFade(@Nullable Screen screen, CallbackInfo ci) {
        ScreenFade.onSetScreen((Minecraft) (Object) this, screen);
    }
}
