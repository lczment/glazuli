package com.lczme.glazuli;

import com.lczme.glazuli.anim.AnimClock;
import com.lczme.glazuli.cave.CaveBackground;
import com.lczme.glazuli.cave.MenuScenes;
import com.lczme.glazuli.compat.Compat;
import com.lczme.glazuli.config.GlazuliConfig;
import com.lczme.glazuli.dev.Bench;
import com.lczme.glazuli.dev.DevHooks;
import com.lczme.glazuli.font.FontPack;
import com.lczme.glazuli.font.FontScale;
import com.lczme.glazuli.intro.StartupSound;
import com.lczme.glazuli.render.BlurFade;
import com.lczme.glazuli.render.BlurPipeline;
import com.lczme.glazuli.render.CreatePanels;
import com.lczme.glazuli.render.DarkText;
import com.lczme.glazuli.render.PackOverrides;
import com.lczme.glazuli.render.QuitFade;
import com.lczme.glazuli.render.ScreenFade;
import com.lczme.glazuli.render.GlazuliShaders;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(value = Glazuli.MODID, dist = Dist.CLIENT)
public final class Glazuli {
    public static final String MODID = "glazuli";
    public static final Logger LOGGER = LogUtils.getLogger();
    public Glazuli(IEventBus modBus, ModContainer container) {
        migrateLegacyConfig();
        container.registerConfig(ModConfig.Type.CLIENT, GlazuliConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(GlazuliShaders::onRegisterShaders);
        modBus.addListener(FontPack::onAddPackFinders);
        modBus.addListener(FontScale::onConfigReloading);
        modBus.addListener((FMLClientSetupEvent event) -> {
            CaveBackground.startGenerating();
            event.enqueueWork(Glazuli::defaultMenuBlurOff);
        });
        modBus.addListener((RegisterClientReloadListenersEvent event) -> {
            event.registerReloadListener(CaveBackground.reloadListener());
            event.registerReloadListener(PackOverrides.reloadListener());
            event.registerReloadListener(CreatePanels.reloadListener());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (RenderFrameEvent.Pre event) -> AnimClock.onFrameStart());
        NeoForge.EVENT_BUS.addListener(BlurPipeline::onRenderFramePre);
        NeoForge.EVENT_BUS.addListener(BlurPipeline::onScreenRenderPre);
        NeoForge.EVENT_BUS.addListener(ScreenFade::onRenderFramePre);
        NeoForge.EVENT_BUS.addListener(ScreenFade::onRenderFramePost);
        NeoForge.EVENT_BUS.addListener(QuitFade::onRenderFramePost);
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> {
            DarkText.reset();
            BlurFade.onFrameStart();
            CaveBackground.onFrameStart();
            StartupSound.onFrameStart();
        });
        NeoForge.EVENT_BUS.addListener(MenuScenes::registerCommands);
        Compat.register(NeoForge.EVENT_BUS);
        DevHooks.register(NeoForge.EVENT_BUS);
        Bench.register(NeoForge.EVENT_BUS);
    }

    /** The mod used to be "liquidglass"; carry its config file and folder over once, then the old blur flag. */
    private static void migrateLegacyConfig() {
        Path dir = FMLPaths.CONFIGDIR.get();
        moveIfAbsent(dir.resolve("liquidglass"), dir.resolve(MODID));
        moveIfAbsent(dir.resolve("liquidglass-client.toml"), dir.resolve(MODID + "-client.toml"));
        // Older versions kept the blur flag in the client config, which NeoForge prunes once the spec loads.
        Path config = dir.resolve(MODID + "-client.toml");
        try {
            if (Files.exists(config) && Files.readString(config).contains("menuBlurDefaulted = true")) {
                writeMenuBlurMarker();
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read {}", config, e);
        }
    }

    private static void moveIfAbsent(Path from, Path to) {
        if (!Files.exists(from) || Files.exists(to)) {
            return;
        }
        try {
            Files.move(from, to);
            LOGGER.info("Migrated {} to {}", from.getFileName(), to.getFileName());
        } catch (IOException e) {
            LOGGER.warn("Could not migrate {} to {}", from, to, e);
        }
    }

    /** Turns the vanilla menu blur down to 0 once, so the menu scene stays sharp; players can raise it again. */
    private static void defaultMenuBlurOff() {
        if (Files.exists(menuBlurMarker())) {
            return;
        }
        Options options = Minecraft.getInstance().options;
        options.menuBackgroundBlurriness().set(0);
        options.save();
        writeMenuBlurMarker();
    }

    private static Path menuBlurMarker() {
        return FMLPaths.CONFIGDIR.get().resolve(MODID).resolve("menu-blur-reset");
    }

    private static void writeMenuBlurMarker() {
        Path marker = menuBlurMarker();
        try {
            Files.createDirectories(marker.getParent());
            if (!Files.exists(marker)) {
                Files.createFile(marker);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", marker, e);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
