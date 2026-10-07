package com.lczme.glazuli.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("fov")
    float glazuli$fov();

    @Accessor("fov")
    void glazuli$setFov(float fov);

    @Accessor("oldFov")
    float glazuli$oldFov();

    @Accessor("oldFov")
    void glazuli$setOldFov(float fov);
}
