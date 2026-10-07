package com.lczme.liquidglass.cave;

import com.lczme.liquidglass.LiquidGlass;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.annotation.Nullable;
import net.neoforged.fml.ModList;

/** Optional hooks into Create (and its library Catnip), only touched reflectively. */
final class CreateCompat {
    private static boolean looked;
    @Nullable
    private static MethodHandle tick;

    private CreateCompat() {
    }

    static boolean isLoaded() {
        return ModList.get().isLoaded("create");
    }

    /**
     * Create animates kinetic parts from Catnip's tick counter, which only advances while a world is being played; the
     * menu scene advances it itself.
     */
    static void tickAnimations() {
        if (!looked) {
            looked = true;
            if (isLoaded()) {
                try {
                    Class<?> holder = Class.forName("net.createmod.catnip.animation.AnimationTickHolder");
                    tick = MethodHandles.publicLookup().findStatic(holder, "tick", MethodType.methodType(void.class));
                } catch (ReflectiveOperationException | LinkageError e) {
                    LiquidGlass.LOGGER.warn("Create's animation clock not found; its machines won't turn in menu scenes", e);
                }
            }
        }
        if (tick != null) {
            try {
                tick.invokeExact();
            } catch (Throwable e) {
                tick = null;
                LiquidGlass.LOGGER.warn("Create's animation clock failed", e);
            }
        }
    }
}
