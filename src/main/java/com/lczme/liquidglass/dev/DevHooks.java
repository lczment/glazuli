package com.lczme.liquidglass.dev;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.lczme.liquidglass.intro.IntroState;
import com.lczme.liquidglass.intro.ReloadOverlay;
import java.util.function.BooleanSupplier;
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
 * {@code -Dliquidglass.autoshot=true} hook that screenshots the startup intro, a resource reload and the demo,
 * then optionally exits.
 */
public final class DevHooks {
    private static final boolean AUTOSHOT = Boolean.getBoolean("liquidglass.autoshot");
    private static final boolean AUTOSHOT_EXIT = Boolean.getBoolean("liquidglass.autoshot.exit");
    // Time-based rather than frame-based so animation timing is the same regardless of frame rate.
    private static final double SETTLED = 1.5;
    private static final double MID_INTRO = 0.2;
    private static final double SAVE_SECONDS = 0.5;
    private static final double RELOAD_TIMEOUT = 10.0;

    // The game event bus only starts once loading has finished, so the startup overlay reports its frames directly.
    private static final BootShot[] BOOT_SHOTS = {
            new BootShot("liquidglass_autoshot_boot_handoff.png", IntroState.Phase.LOADING, 0.3),
            new BootShot("liquidglass_autoshot_boot_loading.png", IntroState.Phase.LOADING, 1.6),
            new BootShot("liquidglass_autoshot_boot_outro.png", IntroState.Phase.OUTRO, 0.45),
    };
    private static final boolean[] bootTaken = new boolean[BOOT_SHOTS.length];

    @Nullable
    private static Screen menu;
    private static int shot = -1;
    private static double shotStart;
    private static double grabTime;
    private static boolean grabbed;

    // In-memory config changes only (ConfigValue#set does not write to disk); glass is re-enabled at the end.
    private static final Shot[] SHOTS = {
            new Shot("liquidglass_autoshot_title.png", 2.5, () -> {
            }),
            new Shot("liquidglass_autoshot_reload.png", RELOAD_TIMEOUT, () -> Minecraft.getInstance().reloadResourcePacks(),
                    () -> Minecraft.getInstance().getOverlay() instanceof ReloadOverlay reload && reload.elapsed() >= 0.8F, true),
            new Shot("liquidglass_autoshot_reload_thaw.png", RELOAD_TIMEOUT, () -> {
            }, () -> Minecraft.getInstance().getOverlay() instanceof ReloadOverlay reload && reload.fadeOutElapsed() >= 0.18F, false),
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

    /** Called by the startup overlay at the end of each of its frames, with seconds since the phase's first frame. */
    public static void onBootFrame(IntroState.Phase phase, double phaseElapsed) {
        if (!AUTOSHOT) {
            return;
        }
        for (int i = 0; i < BOOT_SHOTS.length; i++) {
            BootShot boot = BOOT_SHOTS[i];
            if (!bootTaken[i] && boot.phase() == phase && phaseElapsed >= boot.at()) {
                bootTaken[i] = true;
                grab(boot.fileName());
                return;
            }
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
        double now = AnimClock.now();
        Shot current = SHOTS[shot];
        if (!grabbed && (now - shotStart >= current.settleSeconds() || current.ready() != null && current.ready().getAsBoolean())) {
            grabbed = true;
            grabTime = now;
            grab(current.fileName());
        } else if (grabbed) {
            // Shots that follow an overlay's animation start right away; the others wait for it to be gone.
            boolean waitForOverlay = shot + 1 >= SHOTS.length || SHOTS[shot + 1].waitForOverlay();
            if (!waitForOverlay || now - grabTime >= SAVE_SECONDS && mc.getOverlay() == null) {
                startShot(shot + 1);
            }
        }
    }

    private static void grab(String fileName) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, fileName, mc.getMainRenderTarget(),
                msg -> LiquidGlass.LOGGER.info("[autoshot] {}", msg.getString()));
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

    /**
     * {@code ready}, if set, triggers the grab early; {@code settleSeconds} is then only a timeout.
     * {@code waitForOverlay}: only start once no loading overlay is showing.
     */
    private record Shot(String fileName, double settleSeconds, Runnable setup, @Nullable BooleanSupplier ready, boolean waitForOverlay) {
        Shot(String fileName, double settleSeconds, Runnable setup) {
            this(fileName, settleSeconds, setup, null, true);
        }
    }

    private record BootShot(String fileName, IntroState.Phase phase, double at) {
    }
}
