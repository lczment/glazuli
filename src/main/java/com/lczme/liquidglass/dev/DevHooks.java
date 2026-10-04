package com.lczme.liquidglass.dev;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.compat.sodium.SodiumScreens;
import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.lczme.liquidglass.intro.IntroState;
import com.lczme.liquidglass.intro.ReloadOverlay;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Development-only {@code -Dliquidglass.autoshot=true} hook: screenshots the startup intro, a resource reload, the
 * options screen, (with Sodium installed) the glass video settings and, in a throwaway world, the inventory with JEI's
 * overlay; then optionally exits.
 */
public final class DevHooks {
    private static final boolean AUTOSHOT = Boolean.getBoolean("liquidglass.autoshot");
    private static final boolean AUTOSHOT_EXIT = Boolean.getBoolean("liquidglass.autoshot.exit");
    // Time-based rather than frame-based so animation timing is the same regardless of frame rate.
    private static final double SETTLED = 1.5;
    private static final double SAVE_SECONDS = 0.5;
    private static final double RELOAD_TIMEOUT = 10.0;
    private static final double WORLD_TIMEOUT = 120.0;
    private static final String WORLD = "liquidglass-autoshot";

    // The game event bus only starts once loading has finished, so the startup overlay reports its frames directly.
    private static final BootShot[] BOOT_SHOTS = {
            new BootShot("liquidglass_autoshot_boot_handoff.png", IntroState.Phase.LOADING, 0.3),
            new BootShot("liquidglass_autoshot_boot_loading.png", IntroState.Phase.LOADING, 1.6),
            new BootShot("liquidglass_autoshot_boot_outro.png", IntroState.Phase.OUTRO, 0.45),
            new BootShot("liquidglass_autoshot_boot_zoom.png", IntroState.Phase.OUTRO, 0.95),
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
            new Shot("liquidglass_autoshot_title_letters.png", 1.1, () -> {
            }),
            new Shot("liquidglass_autoshot_title.png", 2.5, () -> {
            }),
            new Shot("liquidglass_autoshot_language.png", 1.0, () -> Minecraft.getInstance().setScreen(
                    new LanguageSelectScreen(menu, Minecraft.getInstance().options, Minecraft.getInstance().getLanguageManager()))),
            // Like LanguageSelectScreen#onDone: start the reload, then return to the parent. The fade-in must show the
            // language screen, not the parent.
            new Shot("liquidglass_autoshot_reload_fadein.png", RELOAD_TIMEOUT, () -> Minecraft.getInstance().tell(() -> {
                Minecraft.getInstance().reloadResourcePacks();
                Minecraft.getInstance().setScreen(menu);
            }), () -> Minecraft.getInstance().getOverlay() instanceof ReloadOverlay reload && reload.elapsed() >= 0.08F, true),
            new Shot("liquidglass_autoshot_reload.png", RELOAD_TIMEOUT, () -> {
            }, () -> Minecraft.getInstance().getOverlay() instanceof ReloadOverlay reload && reload.elapsed() >= 0.8F, false),
            new Shot("liquidglass_autoshot_reload_thaw.png", RELOAD_TIMEOUT, () -> {
            }, () -> Minecraft.getInstance().getOverlay() instanceof ReloadOverlay reload && reload.fadeOutElapsed() >= 0.95F, false),
            new Shot("liquidglass_autoshot_options.png", SETTLED, () -> Minecraft.getInstance().setScreen(
                    new OptionsScreen(menu, Minecraft.getInstance().options))),
            // Opened the way Sodium's options button does; the glass port replaces it on the way in.
            new Shot("liquidglass_autoshot_sodium.png", SETTLED, () -> {
                if (ModList.get().isLoaded("sodium")) {
                    Minecraft.getInstance().setScreen(SodiumScreens.videoSettings(Minecraft.getInstance().screen));
                }
            }),
            new Shot("liquidglass_autoshot_create_world.png", 3.0, () -> CreateWorldScreen.openFresh(Minecraft.getInstance(), menu)),
            new Shot("liquidglass_autoshot_iris.png", SETTLED, () -> openReflective("iris", "net.irisshaders.iris.gui.screen.ShaderPackScreen")),
            new Shot("liquidglass_autoshot_iris_options.png", SETTLED, DevHooks::openIrisOptions),
            new Shot("liquidglass_autoshot_fallback.png", SETTLED, () -> LiquidGlassConfig.ENABLE_GLASS.set(false)),
            new Shot("liquidglass_autoshot_menu.png", SETTLED, () -> {
                LiquidGlassConfig.ENABLE_GLASS.set(true);
                Minecraft.getInstance().setScreen(menu);
            }),
            // In-game screens (JEI's overlay) last: there is no way back to the menu without disconnecting.
            new Shot("liquidglass_autoshot_world.png", WORLD_TIMEOUT, () -> Minecraft.getInstance().tell(DevHooks::enterWorld),
                    () -> Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null, true),
            new Shot("liquidglass_autoshot_inventory.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.setScreen(new InventoryScreen(mc.player));
                }
            }),
            // Hover the first ingredient of JEI's list (tooltip), then open JEI's crafting recipes.
            new Shot("liquidglass_autoshot_jei_hover.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), mc.getWindow().getScreenWidth() * 0.923, mc.getWindow().getScreenHeight() * 0.153);
            }),
            new Shot("liquidglass_autoshot_jei_recipes.png", SETTLED, DevHooks::showJeiCraftingRecipes),
            // Client-side menus are enough to see how container screens render.
            new Shot("liquidglass_autoshot_chest.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    Inventory inventory = mc.player.getInventory();
                    mc.setScreen(new ContainerScreen(ChestMenu.threeRows(0, inventory), inventory, Component.translatable("container.chest")));
                }
            }),
            new Shot("liquidglass_autoshot_furnace.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    Inventory inventory = mc.player.getInventory();
                    mc.setScreen(new FurnaceScreen(new FurnaceMenu(0, inventory), inventory, Component.translatable("container.furnace")));
                }
            }),
            new Shot("liquidglass_autoshot_toast.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(null);
                SystemToast.add(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Liquid Glass"), Component.literal("System toast"));
                mc.getToasts().addToast(new TutorialToast(TutorialToast.Icons.MOVEMENT_KEYS,
                        Component.literal("Tutorial"), Component.literal("Press W to move"), false));
            }),
            new Shot("liquidglass_autoshot_chat.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.getChat().addMessage(Component.literal("<Steve> hello liquid glass"));
                mc.gui.getChat().addMessage(Component.literal("Gold text").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(" and aqua").withStyle(ChatFormatting.AQUA)));
                mc.gui.getChat().addMessage(Component.literal("Grey system message").withStyle(ChatFormatting.GRAY));
                mc.gui.getBossOverlay().update(ClientboundBossEventPacket.createAddPacket(new ServerBossEvent(
                        Component.literal("Ender Dragon"), BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.PROGRESS)));
                mc.setScreen(new ChatScreen("/gamem"));
            }),
            // Look down at the ground so Jade shows its HUD tooltip.
            new Shot("liquidglass_autoshot_jade.png", 2.0, () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(null);
                if (mc.player != null) {
                    mc.player.setXRot(70.0F);
                }
            }),
            new Shot("liquidglass_autoshot_advancements.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.setScreen(new AdvancementsScreen(mc.player.connection.getAdvancements()));
                }
            }),
    };

    /** Switches the open Iris shader pack screen to the pack's option menu, as its "Shader Pack Settings" button does. */
    private static void openIrisOptions() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null || !screen.getClass().getName().equals("net.irisshaders.iris.gui.screen.ShaderPackScreen")) {
            return;
        }
        try {
            java.lang.reflect.Field open = screen.getClass().getDeclaredField("optionMenuOpen");
            open.setAccessible(true);
            open.setBoolean(screen, true);
            screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        } catch (ReflectiveOperationException e) {
            LiquidGlass.LOGGER.warn("[autoshot] could not open Iris options", e);
        }
    }

    private static void openReflective(String modId, String screenClass) {
        if (!ModList.get().isLoaded(modId)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.setScreen((Screen) Class.forName(screenClass).getConstructor(Screen.class).newInstance(mc.screen));
        } catch (ReflectiveOperationException e) {
            LiquidGlass.LOGGER.warn("[autoshot] could not open {}", screenClass, e);
        }
    }

    /** JEI is not on the compile classpath; reflection keeps this dev-only shot free of a hard dependency. */
    private static void showJeiCraftingRecipes() {
        if (!ModList.get().isLoaded("jei")) {
            return;
        }
        try {
            Object runtime = Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
            Object recipesGui = runtime.getClass().getMethod("getRecipesGui").invoke(runtime);
            Object crafting = Class.forName("mezz.jei.api.constants.RecipeTypes").getField("CRAFTING").get(null);
            Class.forName("mezz.jei.api.runtime.IRecipesGui").getMethod("showTypes", List.class).invoke(recipesGui, List.of(crafting));
        } catch (ReflectiveOperationException e) {
            LiquidGlass.LOGGER.warn("[autoshot] could not open JEI recipes", e);
        }
    }

    private static void enterWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getLevelSource().levelExists(WORLD)) {
            mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(menu));
            return;
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                WorldPresets::createNormalWorldDimensions, menu);
    }

    private DevHooks() {
    }

    public static void register(IEventBus gameBus) {
        if (AUTOSHOT) {
            gameBus.addListener(DevHooks::onRenderFramePost);
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
        LiquidGlass.LOGGER.info("[autoshot] {} <- screen {}", fileName, mc.screen == null ? null : mc.screen.getClass().getName());
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
