package com.lczme.liquidglass.dev;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.anim.AnimClock;
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
    // Time-based rather than frame-based so animation timing is the same regardless of frame rate.
    private static final double SETTLED = 1.5;
    private static final double MID_INTRO = 0.2;
    private static final double SAVE_SECONDS = 0.5;

    @Nullable
    private static Screen menu;
    private static int shot = -1;
    private static double shotStart;
    private static boolean grabbed;

    // In-memory config changes only (ConfigValue#set does not write to disk); glass is re-enabled at the end.
    private static final Shot[] SHOTS = {
            new Shot("liquidglass_autoshot_intro.png", MID_INTRO, () -> {
                LiquidGlassConfig.ENABLE_GLASS.set(true);
                Minecraft.getInstance().setScreen(new GlassDemoScreen(menu));
            }),
            new Shot("liquidglass_autoshot.png", SETTLED, () -> Minecraft.getInstance().setScreen(new GlassDemoScreen(menu))),
            new Shot("liquidglass_autoshot_fallback.png", SETTLED, () -> LiquidGlassConfig.ENABLE_GLASS.set(false)),
            new Shot("liquidglass_autoshot_menu.png", SETTLED, () -> {
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
        double elapsed = AnimClock.now() - shotStart;
        double settle = SHOTS[shot].settleSeconds();
        if (!grabbed && elapsed >= settle) {
            grabbed = true;
            Screenshot.grab(mc.gameDirectory, SHOTS[shot].fileName(), mc.getMainRenderTarget(),
                    msg -> LiquidGlass.LOGGER.info("[autoshot] {}", msg.getString()));
        } else if (grabbed && elapsed >= settle + SAVE_SECONDS) {
            startShot(shot + 1);
        }
    }

    private static void startShot(int index) {
        shot = index;
        shotStart = AnimClock.now();
        grabbed = false;
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

    private record Shot(String fileName, double settleSeconds, Runnable setup) {
    }
}
