package com.lczme.glazuli.compat.mixin.sodium;

import com.lczme.glazuli.compat.sodium.gui.SodiumWidgetDimensions;
import net.caffeinemc.mods.sodium.client.gui.widgets.AbstractWidget;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = AbstractWidget.class, remap = false)
public class SodiumAbstractWidgetMixin implements SodiumWidgetDimensions {
    @Mutable
    @Shadow
    @Final
    private Dim2i dim;

    @Override
    public void glazuli$setDimensions(Dim2i dim) {
        this.dim = dim;
    }
}
