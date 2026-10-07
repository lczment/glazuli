package com.lczme.glazuli.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("eyeHeight")
    void glazuli$setEyeHeight(float height);

    @Accessor("eyeHeightOld")
    void glazuli$setEyeHeightOld(float height);
}
