package com.lczme.liquidglass.compat.mixin.sodium;

import com.lczme.liquidglass.compat.sodium.gui.option.OptionExtended;
import com.lczme.liquidglass.compat.sodium.gui.option.OptionStateProvider;
import net.caffeinemc.mods.sodium.client.config.structure.Config;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Option.class)
public class SodiumOptionMixin implements OptionExtended, OptionStateProvider {
    @Shadow
    @Final
    ResourceLocation id;

    @Shadow
    Config state;

    @Override
    public ResourceLocation liquidglass$getId() {
        return id;
    }

    @Override
    public @Nullable Config liquidglass$getParentConfig() {
        return this.state;
    }
}
