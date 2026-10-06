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
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
    // Where the Create shots place their blocks: the block the player looks at once in the world.
    @Nullable
    private static BlockPos createPos;

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
            new Shot("liquidglass_autoshot_sodium_slider.png", SETTLED, () -> hover(220.0, 81.0)),
            new Shot("liquidglass_autoshot_create_world.png", 3.0, () -> CreateWorldScreen.openFresh(Minecraft.getInstance(), menu)),
            new Shot("liquidglass_autoshot_iris.png", SETTLED, () -> openReflective("iris", "net.irisshaders.iris.gui.screen.ShaderPackScreen")),
            new Shot("liquidglass_autoshot_iris_options.png", SETTLED, DevHooks::openIrisOptions),
            new Shot("liquidglass_autoshot_fallback.png", SETTLED, () -> LiquidGlassConfig.ENABLE_GLASS.set(false)),
            new Shot("liquidglass_autoshot_menu.png", SETTLED, () -> {
                LiquidGlassConfig.ENABLE_GLASS.set(true);
                Minecraft.getInstance().setScreen(menu);
            }),
            new Shot("liquidglass_autoshot_create_menu.png", SETTLED, () -> openReflective("create",
                    "com.simibubi.create.infrastructure.gui.CreateMainMenuScreen")),
            new Shot("liquidglass_autoshot_create_config.png", SETTLED, () -> openCreate(() -> (Screen) Class.forName(
                    "net.createmod.catnip.config.ui.BaseConfigScreen").getConstructor(Screen.class, String.class).newInstance(menu, "create"))),
            new Shot("liquidglass_autoshot_ponder_index.png", SETTLED, () -> openCreate(() -> (Screen) Class.forName(
                    "net.createmod.ponder.foundation.ui.PonderIndexScreen").getConstructor().newInstance())),
            // Switching shader packs on the title screen rebuilds Iris' pipeline while the cave is showing.
            // Mid-flight between two camera spots: temporal shader effects show their smearing here.
            new Shot("liquidglass_autoshot_cave_motion.png", 0.7, () -> Minecraft.getInstance().setScreen(
                    new OptionsScreen(menu, Minecraft.getInstance().options))),
            new Shot("liquidglass_autoshot_menu_shader_switch.png", 4.0, () -> {
                Minecraft.getInstance().setScreen(menu);
                switchIrisPack();
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
            // Create: a creative motor where the player looks, seen through goggles; the items for the next shots.
            new Shot("liquidglass_autoshot_create_goggles.png", 2.5, () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(null);
                if (!ModList.get().isLoaded("create") || mc.player == null || !(mc.hitResult instanceof BlockHitResult hit)) {
                    return;
                }
                createPos = hit.getBlockPos();
                mc.player.connection.sendCommand("gamerule sendCommandFeedback false");
                mc.player.connection.sendCommand("time set noon");
                mc.player.connection.sendCommand("item replace entity @s hotbar.0 with create:cogwheel");
                mc.player.connection.sendCommand("item replace entity @s hotbar.1 with create:filter");
                mc.player.connection.sendCommand("item replace entity @s hotbar.2 with air");
                mc.player.connection.sendCommand("item replace entity @s hotbar.3 with create:goggles");
                mc.player.connection.sendCommand(String.format("setblock %d %d %d create:creative_motor[facing=up]",
                        createPos.getX(), createPos.getY(), createPos.getZ()));
                mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(createPos));
                // Put on like a player does; the server handles the packets in order, so the goggles are there by then.
                if (mc.gameMode != null) {
                    mc.player.getInventory().selected = 3;
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    mc.player.getInventory().selected = 2;
                }
            }),
            new Shot("liquidglass_autoshot_create_inventory.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (createPos != null && mc.player != null) {
                    mc.setScreen(new InventoryScreen(mc.player));
                }
            }),
            // Create's item description tooltip on the cogwheel in the first hotbar slot. Opening a screen centres the
            // cursor, hence a shot of its own.
            new Shot("liquidglass_autoshot_create_tooltip.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (!(mc.screen instanceof InventoryScreen inventory)) {
                    return;
                }
                hover(inventory.getGuiLeft() + 16.0, inventory.getGuiTop() + 150.0);
            }),
            new Shot("liquidglass_autoshot_create_filter.png", 5.0, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (createPos == null || mc.player == null || mc.gameMode == null) {
                    return;
                }
                mc.player.closeContainer();
                mc.player.connection.sendCommand(String.format("setblock %d %d %d create:schematic_table",
                        createPos.getX(), createPos.getY(), createPos.getZ()));
                mc.player.getInventory().selected = 1;
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }, () -> isScreen("FilterScreen"), true),
            new Shot("liquidglass_autoshot_create_schematic_table.png", 5.0, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (createPos == null || mc.player == null || mc.gameMode == null) {
                    return;
                }
                mc.player.closeContainer();
                mc.player.getInventory().selected = 2;
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(createPos), Direction.UP, createPos, false));
            }, () -> isScreen("SchematicTableScreen"), true),
            // Ponder scenes need a client level.
            new Shot("liquidglass_autoshot_ponder.png", 3.0, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.closeContainer();
                }
                openCreate(() -> (Screen) Class.forName("net.createmod.ponder.foundation.ui.PonderUI")
                        .getMethod("of", ItemStack.class).invoke(null, createStack("cogwheel")));
            }),
    };

    /**
     * Puts the mouse at GUI coordinates. Moving the OS cursor sends no events while the window is in the background,
     * so this sets the position the game reads instead.
     */
    private static void hover(double guiX, double guiY) {
        Minecraft mc = Minecraft.getInstance();
        try {
            java.lang.reflect.Field x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mc.mouseHandler, guiX * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth());
            y.setDouble(mc.mouseHandler, guiY * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
        } catch (ReflectiveOperationException e) {
            LiquidGlass.LOGGER.warn("[autoshot] could not move the mouse", e);
        }
    }

    private static boolean isScreen(String simpleName) {
        Screen screen = Minecraft.getInstance().screen;
        return screen != null && screen.getClass().getSimpleName().equals(simpleName);
    }

    private static ItemStack createStack(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", path)));
    }

    private interface ScreenFactory {
        Screen create() throws ReflectiveOperationException;
    }

    /** Create is not on the compile classpath; its screens are built reflectively. */
    private static void openCreate(ScreenFactory factory) {
        if (!ModList.get().isLoaded("create")) {
            return;
        }
        try {
            Minecraft.getInstance().setScreen(factory.create());
        } catch (ReflectiveOperationException | RuntimeException e) {
            LiquidGlass.LOGGER.warn("[autoshot] could not open a Create screen", e);
        }
    }

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

    /** Selects the next pack in the shaderpacks folder and reloads Iris, like the shader pack screen's Apply. */
    private static void switchIrisPack() {
        if (!ModList.get().isLoaded("iris")) {
            return;
        }
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object config = iris.getMethod("getIrisConfig").invoke(null);
            java.nio.file.Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("shaderpacks");
            List<String> packs;
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(dir)) {
                packs = files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".zip")).sorted().toList();
            }
            Object current = config.getClass().getMethod("getShaderPackName").invoke(config);
            String currentName = current instanceof java.util.Optional<?> o ? String.valueOf(o.orElse(null)) : String.valueOf(current);
            String next = packs.get((packs.indexOf(currentName) + 1) % packs.size());
            config.getClass().getMethod("setShaderPackName", String.class).invoke(config, next);
            // Iris#reload re-reads the config file, so the choice has to be on disk first.
            config.getClass().getMethod("save").invoke(config);
            iris.getMethod("reload").invoke(null);
            LiquidGlass.LOGGER.info("[autoshot] switched shader pack {} -> {}", currentName, next);
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException e) {
            LiquidGlass.LOGGER.warn("[autoshot] could not switch shader pack", e);
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
