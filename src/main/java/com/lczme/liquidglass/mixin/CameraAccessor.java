package com.lczme.liquidglass.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("eyeHeight")
    void liquidglass$setEyeHeight(float height);

    @Accessor("eyeHeightOld")
    void liquidglass$setEyeHeightOld(float height);
}
