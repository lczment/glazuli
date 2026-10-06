package com.lczme.liquidglass;

import com.lczme.liquidglass.cave.CaveBackground;
import com.lczme.liquidglass.compat.Compat;
import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.lczme.liquidglass.dev.DevHooks;
import com.lczme.liquidglass.font.FontPack;
import com.lczme.liquidglass.font.FontScale;
import com.lczme.liquidglass.render.BlurPipeline;
import com.lczme.liquidglass.render.CreatePanels;
import com.lczme.liquidglass.render.DarkText;
import com.lczme.liquidglass.render.PackOverrides;
import com.lczme.liquidglass.render.QuitFade;
import com.lczme.liquidglass.render.ScreenFade;
import com.lczme.liquidglass.render.LiquidGlassShaders;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
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
        modBus.addListener(FontPack::onAddPackFinders);
        modBus.addListener(FontScale::onConfigReloading);
        modBus.addListener((FMLClientSetupEvent event) -> CaveBackground.startGenerating());
        modBus.addListener((RegisterClientReloadListenersEvent event) -> {
            event.registerReloadListener(CaveBackground.reloadListener());
            event.registerReloadListener(PackOverrides.reloadListener());
            event.registerReloadListener(CreatePanels.reloadListener());
        });
        NeoForge.EVENT_BUS.addListener(BlurPipeline::onRenderFramePre);
        NeoForge.EVENT_BUS.addListener(BlurPipeline::onScreenRenderPre);
        NeoForge.EVENT_BUS.addListener(ScreenFade::onRenderFramePre);
        NeoForge.EVENT_BUS.addListener(ScreenFade::onRenderFramePost);
        NeoForge.EVENT_BUS.addListener(QuitFade::onRenderFramePost);
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> {
            DarkText.reset();
            CaveBackground.onFrameStart();
        });
        Compat.register(NeoForge.EVENT_BUS);
        DevHooks.register(NeoForge.EVENT_BUS);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
