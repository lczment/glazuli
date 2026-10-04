package com.lczme.liquidglass.dev;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.config.LiquidGlassConfig;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Development-only helpers: a title screen button that opens {@link GlassDemoScreen}, and the
 * {@code -Dliquidglass.autoshot=true} hook that opens the demo, screenshots it and optionally exits.
 */
public final class DevHooks {
    private static final boolean AUTOSHOT = Boolean.getBoolean("liquidglass.autoshot");
    private static final boolean AUTOSHOT_EXIT = Boolean.getBoolean("liquidglass.autoshot.exit");
    private static final int SETTLE_FRAMES = 60;
    private static final int SAVE_FRAMES = 30;

    @Nullable
    private static Screen menu;
    private static int shot = -1;
    private static int frames;

    // In-memory config changes only (ConfigValue#set does not write to disk); glass is re-enabled at the end.
    private static final Shot[] SHOTS = {
            new Shot("liquidglass_autoshot.png", () -> {
                LiquidGlassConfig.ENABLE_GLASS.set(true);
                Minecraft.getInstance().setScreen(new GlassDemoScreen(menu));
            }),
            new Shot("liquidglass_autoshot_fallback.png", () -> LiquidGlassConfig.ENABLE_GLASS.set(false)),
            new Shot("liquidglass_autoshot_menu.png", () -> {
                LiquidGlassConfig.ENABLE_GLASS.set(true);
                Minecraft.getInstance().setScreen(menu);
            }),
    };

    private DevHooks() {
    }

    public static void register(IEventBus gameBus) {
        if (!FMLEnvironment.production) {
            gameBus.addListener(DevHooks::onScreenInit);
        }
        if (AUTOSHOT) {
            gameBus.addListener(DevHooks::onRenderFramePost);
        }
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen title) {
            event.addListener(Button.builder(Component.translatable("liquidglass.demo.open"),
                            b -> Minecraft.getInstance().setScreen(new GlassDemoScreen(title)))
                    .bounds(4, 4, 80, 20)
                    .build());
        }
    }

    private static void onRenderFramePost(RenderFrameEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (shot < 0) {
            // Any menu once loading finished (first launch shows the accessibility onboarding instead of the title).
            if (mc.getOverlay() == null && mc.screen != null) {
                menu = mc.screen;
                startShot(0);
            }
            return;
        }
        if (shot >= SHOTS.length) {
            return;
        }
        frames++;
        if (frames == SETTLE_FRAMES) {
            Screenshot.grab(mc.gameDirectory, SHOTS[shot].fileName(), mc.getMainRenderTarget(),
                    msg -> LiquidGlass.LOGGER.info("[autoshot] {}", msg.getString()));
        } else if (frames >= SETTLE_FRAMES + SAVE_FRAMES) {
            startShot(shot + 1);
        }
    }

    private static void startShot(int index) {
        shot = index;
        frames = 0;
        if (index < SHOTS.length) {
            SHOTS[index].setup().run();
        } else {
            LiquidGlassConfig.ENABLE_GLASS.set(true);
            if (AUTOSHOT_EXIT) {
                LiquidGlass.LOGGER.info("[autoshot] done, stopping client");
                Minecraft.getInstance().stop();
            }
        }
    }

    private record Shot(String fileName, Runnable setup) {
    }
}
