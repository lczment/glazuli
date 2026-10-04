package com.lczme.liquidglass.mixin;

import com.lczme.liquidglass.intro.IntroOverlays;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "setOverlay", at = @At("HEAD"), argsOnly = true)
    @Nullable
    private Overlay liquidglass$replaceLoadingOverlay(@Nullable Overlay overlay) {
        return IntroOverlays.replace((Minecraft) (Object) this, overlay);
    }
}
