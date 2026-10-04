package com.lczme.liquidglass.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("fov")
    float liquidglass$fov();

    @Accessor("fov")
    void liquidglass$setFov(float fov);

    @Accessor("oldFov")
    float liquidglass$oldFov();

    @Accessor("oldFov")
    void liquidglass$setOldFov(float fov);
}
