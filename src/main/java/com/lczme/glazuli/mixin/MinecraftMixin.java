package com.lczme.glazuli.mixin;

import com.lczme.glazuli.cave.CaveBackground;
import com.lczme.glazuli.dev.Perf;
import com.lczme.glazuli.font.FontScale;
import com.lczme.glazuli.intro.IntroOverlays;
import com.lczme.glazuli.intro.IntroState;
import com.lczme.glazuli.render.QuitFade;
import com.lczme.glazuli.render.ScreenFade;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
     * cave camera, so they get the player's own limit. Not while the window is in the background: nobody is watching,
     * and the shader scene would keep the GPU at full load.
     */
    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void glazuli$uncapAnimatedMenus(CallbackInfoReturnable<Integer> cir) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.level != null || !minecraft.isWindowActive() && !Perf.ON) {
            return;
        }
        // Called every frame from the start: CaveBackground may only be touched once mod loading is over, as its
        // dependencies (blocks, render types) must not be class-loaded while mods register.
        if (IntroState.isPlaying() || IntroState.isDone() && minecraft.getOverlay() == null && CaveBackground.isActive()) {
            int limit = minecraft.getWindow().getFramerateLimit();
            // "Unlimited" would draw the menu thousands of times a second without a shader pack. The cap leaves twice
            // the refresh rate (at least 1.5 times): vanilla's limiter runs a little slow and jittery, and a cap at the
            // refresh rate itself would repeat a frame every so often. Above that it stays unlimited.
            if (limit >= Options.UNLIMITED_FRAMERATE_CUTOFF && !Perf.ON) {
                int refresh = glazuli$refreshRate(minecraft);
                if (2 * refresh < Options.UNLIMITED_FRAMERATE_CUTOFF) {
                    limit = 2 * refresh;
                } else if (3 * refresh / 2 < Options.UNLIMITED_FRAMERATE_CUTOFF) {
                    limit = Options.UNLIMITED_FRAMERATE_CUTOFF - 1;
                }
            }
            cir.setReturnValue(limit);
        }
    }

    @Unique
    private static int glazuli$refreshRate;
    @Unique
    private static long glazuli$refreshCheckedAt;

    @Unique
    private static int glazuli$refreshRate(Minecraft minecraft) {
        long now = Util.getMillis();
        if (glazuli$refreshRate == 0 || now - glazuli$refreshCheckedAt > 2000L) {
            glazuli$refreshCheckedAt = now;
            glazuli$refreshRate = Math.max(60, minecraft.getWindow().getRefreshRate());
        }
        return glazuli$refreshRate;
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void glazuli$snapshotForFade(@Nullable Screen screen, CallbackInfo ci) {
        ScreenFade.onSetScreen((Minecraft) (Object) this, screen);
    }
}
