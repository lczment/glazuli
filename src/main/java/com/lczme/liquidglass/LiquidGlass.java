package com.lczme.liquidglass;

import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.lczme.liquidglass.dev.DevHooks;
import com.lczme.liquidglass.render.BlurPipeline;
import com.lczme.liquidglass.render.LiquidGlassShaders;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(value = LiquidGlass.MODID, dist = Dist.CLIENT)
public final class LiquidGlass {
    public static final String MODID = "liquidglass";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LiquidGlass(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, LiquidGlassConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(LiquidGlassShaders::onRegisterShaders);
        NeoForge.EVENT_BUS.addListener(BlurPipeline::onRenderFramePre);
        DevHooks.register(NeoForge.EVENT_BUS);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
