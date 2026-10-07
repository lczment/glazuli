package com.lczme.glazuli.mixin;

import com.lczme.glazuli.cave.ShaderCave;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Flywheel (Create's renderer) instances block entities per level and updates them from that level's ticks; the menu
 * scene's level never ticks and is only "the" level while it is drawn, so its block entities use their plain renderers.
 */
@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl", remap = false)
public abstract class FlywheelVisualizationMixin {
    @Inject(method = "supportsVisualization", at = @At("HEAD"), cancellable = true, require = 0)
    private static void glazuli$notForMenuScenes(LevelAccessor level, CallbackInfoReturnable<Boolean> cir) {
        if (ShaderCave.isSceneLevel(level)) {
            cir.setReturnValue(false);
        }
    }
}
