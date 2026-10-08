package com.lczme.glazuli.dev;

import com.lczme.glazuli.Glazuli;
import com.lczme.glazuli.anim.AnimClock;
import com.lczme.glazuli.cave.CaveBackground;
import com.lczme.glazuli.cave.MenuScenes;
import com.lczme.glazuli.cave.SceneClock;
import com.lczme.glazuli.cave.ScenePickerScreen;
import com.lczme.glazuli.compat.sodium.SodiumScreens;
import com.lczme.glazuli.config.GlazuliConfig;
import com.lczme.glazuli.intro.IntroState;
import com.lczme.glazuli.intro.ReloadOverlay;
import java.time.LocalTime;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
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
 * Development-only {@code -Dglazuli.autoshot=true} hook: screenshots the startup intro, a resource reload, the
 * options screen, (with Sodium installed) the glass video settings and, in a throwaway world, the inventory with JEI's
 * overlay; then optionally exits.
 */
public final class DevHooks {
    private static final boolean AUTOSHOT = Boolean.getBoolean("glazuli.autoshot");
    private static final boolean AUTOSHOT_EXIT = Boolean.getBoolean("glazuli.autoshot.exit");
    private static final String ONLY = System.getProperty("glazuli.autoshot.only", "");
    // Time-based rather than frame-based so animation timing is the same regardless of frame rate.
    private static final double SETTLED = 1.5;
    private static final double SAVE_SECONDS = 0.5;
    private static final double RELOAD_TIMEOUT = 10.0;
    private static final double WORLD_TIMEOUT = 120.0;
    private static final double SCENE_TIMEOUT = 30.0;
    private static final String WORLD = "glazuli-autoshot";

    // The game event bus only starts once loading has finished, so the startup overlay reports its frames directly.
    private static final BootShot[] BOOT_SHOTS = {
            new BootShot("glazuli_autoshot_boot_handoff.png", IntroState.Phase.LOADING, 0.3),
            new BootShot("glazuli_autoshot_boot_loading.png", IntroState.Phase.LOADING, 1.6),
            new BootShot("glazuli_autoshot_boot_outro.png", IntroState.Phase.OUTRO, 0.45),
            new BootShot("glazuli_autoshot_boot_zoom.png", IntroState.Phase.OUTRO, 0.95),
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
            new Shot("glazuli_autoshot_title_letters.png", 1.1, () -> {
            }),
            new Shot("glazuli_autoshot_title.png", 2.5, () -> {
            }),
            new Shot("glazuli_autoshot_language.png", 1.0, () -> Minecraft.getInstance().setScreen(
                    new LanguageSelectScreen(menu, Minecraft.getInstance().options, Minecraft.getInstance().getLanguageManager()))),
            // Like LanguageSelectScreen#onDone: start the reload, then return to the parent. The fade-in must show the
            // language screen, not the parent.
            new Shot("glazuli_autoshot_reload_fadein.png", RELOAD_TIMEOUT, () -> Minecraft.getInstance().tell(() -> {
                Minecraft.getInstance().reloadResourcePacks();
                Minecraft.getInstance().setScreen(menu);
            }), () -> Minecraft.getInstance().getOverlay() instanceof ReloadOverlay reload && reload.elapsed() >= 0.08F, true),
            new Shot("glazuli_autoshot_reload.png", RELOAD_TIMEOUT, () -> {
            }, () -> Minecraft.getInstance().getOverlay() instanceof ReloadOverlay reload && reload.elapsed() >= 0.8F, false),
            new Shot("glazuli_autoshot_reload_thaw.png", RELOAD_TIMEOUT, () -> {
            }, () -> Minecraft.getInstance().getOverlay() instanceof ReloadOverlay reload && reload.fadeOutElapsed() >= 0.95F, false),
            new Shot("glazuli_autoshot_options.png", SETTLED, () -> Minecraft.getInstance().setScreen(
                    new OptionsScreen(menu, Minecraft.getInstance().options))),
            // Opened the way Sodium's options button does; the glass port replaces it on the way in.
            new Shot("glazuli_autoshot_sodium.png", SETTLED, () -> {
                if (ModList.get().isLoaded("sodium")) {
                    Minecraft.getInstance().setScreen(SodiumScreens.videoSettings(Minecraft.getInstance().screen));
                }
            }),
            new Shot("glazuli_autoshot_sodium_slider.png", SETTLED, () -> hover(220.0, 81.0)),
            new Shot("glazuli_autoshot_sodium_tab_mid.png", 0.1, () -> clickAt(0.12, 0.403), null, false),
            new Shot("glazuli_autoshot_sodium_tab_done.png", 0.8, () -> { }, null, false),
            new Shot("glazuli_autoshot_create_world.png", 3.0, () -> CreateWorldScreen.openFresh(Minecraft.getInstance(), menu)),
            new Shot("glazuli_autoshot_create_world_tab_mid.png", 0.1, () -> clickAt(0.5, 12.0 / Minecraft.getInstance()
                    .getWindow().getGuiScaledHeight()), null, false),
            new Shot("glazuli_autoshot_create_world_tab_done.png", 0.8, () -> { }, null, false),
            new Shot("glazuli_autoshot_iris.png", SETTLED, () -> openReflective("iris", "net.irisshaders.iris.gui.screen.ShaderPackScreen")),
            new Shot("glazuli_autoshot_iris_options.png", SETTLED, DevHooks::openIrisOptions),
            new Shot("glazuli_autoshot_fallback.png", SETTLED, () -> GlazuliConfig.ENABLE_GLASS.set(false)),
            new Shot("glazuli_autoshot_menu.png", SETTLED, () -> {
                GlazuliConfig.ENABLE_GLASS.set(true);
                Minecraft.getInstance().setScreen(menu);
            }),
            new Shot("glazuli_autoshot_scene_picker.png", SCENE_TIMEOUT, () -> {
                Minecraft.getInstance().setScreen(new ScenePickerScreen(menu));
                CaveBackground.select("lush", false);
            }, () -> CaveBackground.isShowing("lush"), true),
            new Shot("glazuli_autoshot_scene_picker_turn_mid.png", 0.1, () -> {
                if (Minecraft.getInstance().screen instanceof ScenePickerScreen picker) {
                    picker.children().stream().filter(child -> child instanceof Button button && button.active
                                    && (button.getMessage().getString().equals(">") || button.getMessage().getString().equals("<")))
                            .reduce((first, second) -> second).ifPresent(child -> ((Button) child).onPress());
                }
            }, null, false),
            new Shot("glazuli_autoshot_scene_picker_turn_done.png", 0.8, () -> { }, null, false),
            sceneShot("deep_dark", ""),
            // First fully revealed frame vs a while later: nothing may still be loading in.
            new Shot("glazuli_autoshot_scene_ice_reveal.png", SCENE_TIMEOUT, () -> CaveBackground.select("ice", false),
                    () -> CaveBackground.isShowing("ice", 0.0), true),
            new Shot("glazuli_autoshot_scene_ice_late.png", SCENE_TIMEOUT, () -> { },
                    () -> CaveBackground.isShowing("ice", 2.5), true),
            sceneShot("nether", ""),
            sceneShot("ocean", ""),
            sceneShot("mine", ""),
            new Shot("glazuli_autoshot_scene_mine_intro1.png", 0.3, CaveBackground::devReplayIntro, null, false),
            new Shot("glazuli_autoshot_scene_mine_intro2.png", 1.3, () -> { }, null, false),
            new Shot("glazuli_autoshot_scene_mine_intro3.png", 1.3, () -> { }, null, false),
            new Shot("glazuli_autoshot_scene_mine_intro4.png", 1.3, () -> { }, null, false),
            new Shot("glazuli_autoshot_scene_mine_intro5.png", 1.3, () -> { }, null, false),
            new Shot("glazuli_autoshot_scene_mine_intro6.png", 2.0, () -> { }, null, false),
            spotShot("mine", "WORLDS"), spotShot("mine", "SERVERS"), spotShot("mine", "OPTIONS"),
            spotShot("mine", "MODS"), spotShot("mine", "OTHER"),
            new Shot("glazuli_autoshot_scene_mine_flight1.png", 0.7, () -> CaveBackground.devSpot("WORLDS"), null, false),
            new Shot("glazuli_autoshot_scene_mine_turned.png", 2.5, () -> { }, null, false),
            sceneShot("plains_village", ""),
            spotShot("plains_village", "WORLDS"), spotShot("plains_village", "SERVERS"), spotShot("plains_village", "OPTIONS"),
            spotShot("plains_village", "MODS"), spotShot("plains_village", "OTHER"),
            clockShot("plains_village", "", LocalTime.of(12, 0)),
            clockShot("plains_village", "", LocalTime.of(18, 15)),
            clockShot("plains_village", "", LocalTime.of(23, 0)),
            sceneShot("snowy_village", ""),
            spotShot("snowy_village", "WORLDS"), spotShot("snowy_village", "SERVERS"), spotShot("snowy_village", "OPTIONS"),
            spotShot("snowy_village", "MODS"), spotShot("snowy_village", "OTHER"),
            sceneShot("desert_village", ""),
            spotShot("desert_village", "WORLDS"), spotShot("desert_village", "SERVERS"), spotShot("desert_village", "OPTIONS"),
            spotShot("desert_village", "MODS"), spotShot("desert_village", "OTHER"),
            sceneShot("ancient_city", ""),
            spotShot("ancient_city", "WORLDS"), spotShot("ancient_city", "SERVERS"), spotShot("ancient_city", "OPTIONS"),
            spotShot("ancient_city", "MODS"), spotShot("ancient_city", "OTHER"),
            sceneShot("trial_chambers", ""),
            spotShot("trial_chambers", "WORLDS"), spotShot("trial_chambers", "SERVERS"), spotShot("trial_chambers", "OPTIONS"),
            spotShot("trial_chambers", "MODS"), spotShot("trial_chambers", "OTHER"),
            sceneShot("bastion", ""),
            spotShot("bastion", "WORLDS"), spotShot("bastion", "SERVERS"), spotShot("bastion", "OPTIONS"),
            spotShot("bastion", "MODS"), spotShot("bastion", "OTHER"),
            sceneShot("end_city", ""),
            spotShot("end_city", "WORLDS"), spotShot("end_city", "SERVERS"), spotShot("end_city", "OPTIONS"),
            spotShot("end_city", "MODS"), spotShot("end_city", "OTHER"),
            sceneShot("create_factory", ""),
            new Shot("glazuli_autoshot_scene_create_factory_rest.png", SCENE_TIMEOUT, () -> { },
                    () -> CaveBackground.isShowing("create_factory", 5.0), true),
            new Shot("glazuli_autoshot_scene_create_factory_belt.png", 2.5, () -> CaveBackground.devCamera(
                    new org.joml.Vector3f(42.0F, 10.0F, 47.5F), new org.joml.Vector3f(42.0F, 4.0F, 42.5F))),
            new Shot("glazuli_autoshot_scene_create_factory_belt_end.png", 2.5, () -> CaveBackground.devCamera(
                    new org.joml.Vector3f(50.5F, 7.0F, 45.5F), new org.joml.Vector3f(47.5F, 4.0F, 42.5F))),
            new Shot("glazuli_autoshot_scene_create_factory_belt_end2.png", 0.4, () -> { }, null, false),
            new Shot("glazuli_autoshot_scene_create_factory_belt_done.png", 0.1, () -> CaveBackground.devCamera(null, null)),
            spotShot("create_factory", "WORLDS"), spotShot("create_factory", "SERVERS"), spotShot("create_factory", "OPTIONS"),
            spotShot("create_factory", "MODS"), spotShot("create_factory", "OTHER"),
            new Shot("glazuli_autoshot_scene_custom.png", SCENE_TIMEOUT, () -> {
                CaveBackground.devSpot(null);
                MenuScenes.list().stream().filter(entry -> entry.id().startsWith("custom:")).findFirst()
                        .ifPresent(entry -> CaveBackground.select(entry.id(), false));
            },
                    () -> !CaveBackground.scene().startsWith("custom:") || CaveBackground.isShowing(CaveBackground.scene()), true),
            new Shot("glazuli_autoshot_scene_back.png", SCENE_TIMEOUT, () -> {
                CaveBackground.select("cave", false);
                Minecraft.getInstance().setScreen(menu);
            }, () -> CaveBackground.isShowing("cave"), true),
            new Shot("glazuli_autoshot_title_classic.png", SETTLED, () -> {
                GlazuliConfig.CLASSIC_UI.set(true);
                Minecraft.getInstance().setScreen(new TitleScreen());
            }),
            new Shot("glazuli_autoshot_classic_options.png", SETTLED, () -> Minecraft.getInstance().setScreen(
                    new OptionsScreen(menu, Minecraft.getInstance().options))),
            new Shot("glazuli_autoshot_classic_sodium.png", SETTLED, () -> {
                if (ModList.get().isLoaded("sodium")) {
                    Minecraft.getInstance().setScreen(SodiumScreens.videoSettings(Minecraft.getInstance().screen));
                }
            }),
            new Shot("glazuli_autoshot_create_menu.png", SETTLED, () -> {
                GlazuliConfig.CLASSIC_UI.set(false);
                openReflective("create", "com.simibubi.create.infrastructure.gui.CreateMainMenuScreen");
            }),
            new Shot("glazuli_autoshot_create_config.png", SETTLED, () -> openCreate(() -> (Screen) Class.forName(
                    "net.createmod.catnip.config.ui.BaseConfigScreen").getConstructor(Screen.class, String.class).newInstance(menu, "create"))),
            new Shot("glazuli_autoshot_create_config_factory_0.png", 2.0 * SCENE_TIMEOUT, () -> {
                CaveBackground.select("create_factory", false);
                openCreate(() -> (Screen) Class.forName("net.createmod.catnip.config.ui.BaseConfigScreen")
                        .getConstructor(Screen.class, String.class).newInstance(menu, "create"));
            }, () -> CaveBackground.isShowing("create_factory", 2.0), true),
            new Shot("glazuli_autoshot_create_config_factory_1.png", 0.03, () -> { }, null, false),
            new Shot("glazuli_autoshot_create_config_factory_2.png", 0.03, () -> { }, null, false),
            new Shot("glazuli_autoshot_create_config_factory_3.png", 0.03, () -> { }, null, false),
            new Shot("glazuli_autoshot_create_config_factory_4.png", 0.03, () -> { }, null, false),
            new Shot("glazuli_autoshot_ponder_index.png", SETTLED, () -> openCreate(() -> (Screen) Class.forName(
                    "net.createmod.ponder.foundation.ui.PonderIndexScreen").getConstructor().newInstance())),
            // Switching shader packs on the title screen rebuilds Iris' pipeline while the cave is showing.
            // Mid-flight between two camera spots: temporal shader effects show their smearing here.
            new Shot("glazuli_autoshot_cave_motion.png", 0.7, () -> Minecraft.getInstance().setScreen(
                    new OptionsScreen(menu, Minecraft.getInstance().options))),
            // The pack stays loaded but the scene uses Glazuli's own lighting.
            new Shot("glazuli_autoshot_menu_shader_scene_off.png", 4.0, () -> {
                Minecraft.getInstance().setScreen(menu);
                GlazuliConfig.SHADER_SCENE.set(false);
            }),
            new Shot("glazuli_autoshot_menu_shader_switch.png", 4.0, () -> {
                GlazuliConfig.SHADER_SCENE.set(true);
                Minecraft.getInstance().setScreen(menu);
                switchIrisPack();
            }),
            new Shot("glazuli_autoshot_menu_shaders_off.png", 4.0, DevHooks::disableIrisShaders),
            sceneShot("lush", "_plain"),
            sceneShot("deep_dark", "_plain"),
            sceneShot("ice", "_plain"),
            sceneShot("nether", "_plain"),
            sceneShot("ocean", "_plain"),
            sceneShot("plains_village", "_plain"),
            clockShot("plains_village", "_plain", LocalTime.of(12, 0)),
            clockShot("plains_village", "_plain", LocalTime.of(18, 15)),
            clockShot("plains_village", "_plain", LocalTime.of(23, 0)),
            sceneShot("create_factory", "_plain"),
            // Without a shader pack's temporal jitter, twice a moment apart once the camera is held still: only the
            // machines may differ.
            new Shot("glazuli_autoshot_scene_create_factory_plain_still.png", SCENE_TIMEOUT, () -> CaveBackground.freezeCameraDrift(true),
                    () -> CaveBackground.isShowing("create_factory", 4.0), true),
            new Shot("glazuli_autoshot_scene_create_factory_plain_turned.png", 0.3, () -> CaveBackground.freezeCameraDrift(true)),
            new Shot("glazuli_autoshot_scene_create_factory_plain_moving.png", 0.1, () -> CaveBackground.freezeCameraDrift(false)),
            sceneShot("custom:autoshot", "_plain"),
            sceneShot("cave", "_plain"),
            // In-game screens (JEI's overlay) last: there is no way back to the menu without disconnecting.
            new Shot("glazuli_autoshot_world.png", WORLD_TIMEOUT, () -> Minecraft.getInstance().tell(DevHooks::enterWorld),
                    () -> Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null, true),
            new Shot("glazuli_autoshot_scene_capture.png", 3.0, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.connection.sendCommand("menuscene save autoshot");
                }
            }),
            new Shot("glazuli_autoshot_inventory.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                CaveBackground.select("cave", false);
                if (mc.player != null) {
                    mc.setScreen(new InventoryScreen(mc.player));
                }
            }),
            new Shot("glazuli_autoshot_ingame_vanilla_inventory.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                GlazuliConfig.IN_GAME_UI.set(GlazuliConfig.InGameUi.VANILLA);
                if (mc.player != null) {
                    mc.setScreen(new InventoryScreen(mc.player));
                }
            }),
            new Shot("glazuli_autoshot_ingame_vanilla_pause.png", SETTLED, () -> Minecraft.getInstance().setScreen(new PauseScreen(true))),
            new Shot("glazuli_autoshot_ingame_glass_pause.png", SETTLED, () -> {
                GlazuliConfig.IN_GAME_UI.set(GlazuliConfig.InGameUi.SAME_AS_MENU);
                Minecraft.getInstance().setScreen(new PauseScreen(true));
            }),
            // Hover the first ingredient of JEI's list (tooltip), then open JEI's crafting recipes.
            new Shot("glazuli_autoshot_jei_hover.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), mc.getWindow().getScreenWidth() * 0.923, mc.getWindow().getScreenHeight() * 0.153);
            }),
            new Shot("glazuli_autoshot_jei_recipes.png", SETTLED, DevHooks::showJeiCraftingRecipes),
            // Client-side menus are enough to see how container screens render.
            new Shot("glazuli_autoshot_chest.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    Inventory inventory = mc.player.getInventory();
                    mc.setScreen(new ContainerScreen(ChestMenu.threeRows(0, inventory), inventory, Component.translatable("container.chest")));
                }
            }),
            new Shot("glazuli_autoshot_furnace.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    Inventory inventory = mc.player.getInventory();
                    mc.setScreen(new FurnaceScreen(new FurnaceMenu(0, inventory), inventory, Component.translatable("container.furnace")));
                }
            }),
            new Shot("glazuli_autoshot_toast.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(null);
                SystemToast.add(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Glazuli"), Component.literal("System toast"));
                mc.getToasts().addToast(new TutorialToast(TutorialToast.Icons.MOVEMENT_KEYS,
                        Component.literal("Tutorial"), Component.literal("Press W to move"), false));
            }),
            new Shot("glazuli_autoshot_chat.png", SETTLED, () -> {
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
            new Shot("glazuli_autoshot_jade.png", 2.0, () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(null);
                if (mc.player != null) {
                    mc.player.setXRot(70.0F);
                }
            }),
            new Shot("glazuli_autoshot_advancements.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.setScreen(new AdvancementsScreen(mc.player.connection.getAdvancements()));
                }
            }),
            // Create: a creative motor where the player looks, seen through goggles; the items for the next shots.
            new Shot("glazuli_autoshot_create_goggles.png", 2.5, () -> {
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
            new Shot("glazuli_autoshot_create_inventory.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (createPos != null && mc.player != null) {
                    mc.setScreen(new InventoryScreen(mc.player));
                }
            }),
            // Create's item description tooltip on the cogwheel in the first hotbar slot. Opening a screen centres the
            // cursor, hence a shot of its own.
            new Shot("glazuli_autoshot_create_tooltip.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (!(mc.screen instanceof InventoryScreen inventory)) {
                    return;
                }
                hover(inventory.getGuiLeft() + 16.0, inventory.getGuiTop() + 150.0);
            }),
            new Shot("glazuli_autoshot_create_filter.png", 5.0, () -> {
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
            new Shot("glazuli_autoshot_create_schematic_table.png", 5.0, () -> {
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
            new Shot("glazuli_autoshot_ponder.png", 3.0, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.closeContainer();
                }
                openCreate(() -> (Screen) Class.forName("net.createmod.ponder.foundation.ui.PonderUI")
                        .getMethod("of", ItemStack.class).invoke(null, createStack("cogwheel")));
            }),
            // Other common mods; each shot does nothing when its mod is missing. Controlling replaces the key binds screen.
            new Shot("glazuli_autoshot_compat_keybinds.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(new KeyBindsScreen(new PauseScreen(true), mc.options));
            }),
            // AppleSkin's food values in the tooltip of a hotbar item.
            new Shot("glazuli_autoshot_compat_food_tooltip.png", SETTLED, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) {
                    return;
                }
                mc.player.connection.sendCommand("item replace entity @s hotbar.4 with minecraft:cooked_beef");
                InventoryScreen inventory = new InventoryScreen(mc.player);
                mc.setScreen(inventory);
                hover(inventory.getGuiLeft() + 88.0, inventory.getGuiTop() + 150.0);
            }),
            new Shot("glazuli_autoshot_compat_curios.png", 5.0, () -> pressKey("key.keyboard.g"),
                    () -> screenNameContains("Curios"), true),
            new Shot("glazuli_autoshot_compat_worldmap.png", 5.0, () -> pressKey("key.keyboard.m"),
                    () -> isScreen("GuiMap"), true),
            new Shot("glazuli_autoshot_compat_minimap.png", 2.0, () -> Minecraft.getInstance().setScreen(null)),
            new Shot("glazuli_autoshot_compat_backpack.png", 5.0, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (!ModList.get().isLoaded("sophisticatedbackpacks") || mc.player == null || mc.gameMode == null) {
                    return;
                }
                mc.player.connection.sendCommand("item replace entity @s hotbar.5 with sophisticatedbackpacks:backpack");
                mc.tell(() -> {
                    mc.player.getInventory().selected = 5;
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                });
            }, () -> screenNameContains("Backpack"), true),
            // The first use of a waystone activates it, the second opens its menu.
            new Shot("glazuli_autoshot_compat_waystone_activate.png", 3.0, () -> useWaystone(true)),
            new Shot("glazuli_autoshot_compat_waystone.png", 5.0, () -> useWaystone(false),
                    () -> screenNameContains("Waystone"), true),
            // Back to the title: the scene stays dark until the world renderer has meshed it again, then fades in.
            new Shot("glazuli_autoshot_back_to_title_early.png", WORLD_TIMEOUT, () -> Minecraft.getInstance().tell(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    mc.level.disconnect();
                    mc.disconnect(new GenericMessageScreen(Component.literal("Saving")));
                }
                mc.setScreen(new TitleScreen());
            }), () -> Minecraft.getInstance().level == null && Minecraft.getInstance().screen instanceof TitleScreen, true),
            new Shot("glazuli_autoshot_back_to_title_mid.png", 0.3, () -> { }, null, false),
            new Shot("glazuli_autoshot_back_to_title.png", 4.0, () -> { }, null, false),
    };

    @Nullable
    private static BlockPos waystonePos;

    private static void useWaystone(boolean place) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        if (!ModList.get().isLoaded("waystones") || mc.player == null || mc.gameMode == null) {
            return;
        }
        if (place) {
            // Placed like a player does, so the waystone is registered and gets a name; setblock leaves it invalid.
            waystonePos = mc.player.blockPosition().relative(mc.player.getDirection(), 2);
            BlockPos ground = waystonePos.below();
            mc.player.connection.sendCommand(String.format("fill %d %d %d %d %d %d air", waystonePos.getX(), waystonePos.getY(),
                    waystonePos.getZ(), waystonePos.getX(), waystonePos.getY() + 1, waystonePos.getZ()));
            mc.player.connection.sendCommand(String.format("setblock %d %d %d stone", ground.getX(), ground.getY(), ground.getZ()));
            mc.player.connection.sendCommand("item replace entity @s hotbar.6 with waystones:waystone");
            mc.tell(() -> {
                mc.player.getInventory().selected = 6;
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false));
            });
            return;
        }
        if (waystonePos == null) {
            return;
        }
        BlockPos pos = waystonePos;
        mc.tell(() -> {
            mc.player.connection.sendCommand("item replace entity @s hotbar.6 with air");
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        });
    }

    /** Presses a key as if typed in game, for mods that open their screens from a key mapping. */
    private static void pressKey(String key) {
        Minecraft.getInstance().setScreen(null);
        KeyMapping.click(InputConstants.getKey(key));
    }

    private static boolean screenNameContains(String part) {
        Screen screen = Minecraft.getInstance().screen;
        return screen != null && screen.getClass().getName().contains(part);
    }

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
            Glazuli.LOGGER.warn("[autoshot] could not move the mouse", e);
        }
    }

    /** Clicks the open screen at a fraction of its GUI size, as a mouse press and release there would. */
    private static void clickAt(double fractionX, double fractionY) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen != null) {
            double x = fractionX * mc.getWindow().getGuiScaledWidth();
            double y = fractionY * mc.getWindow().getGuiScaledHeight();
            screen.mouseClicked(x, y, 0);
            screen.mouseReleased(x, y, 0);
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
            Glazuli.LOGGER.warn("[autoshot] could not open a Create screen", e);
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
            Glazuli.LOGGER.warn("[autoshot] could not open Iris options", e);
        }
    }

    /** Turns shaders off at runtime and reloads Iris, like the shader pack screen's toggle and Apply. */
    private static void disableIrisShaders() {
        if (!ModList.get().isLoaded("iris")) {
            return;
        }
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object config = iris.getMethod("getIrisConfig").invoke(null);
            config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, false);
            config.getClass().getMethod("save").invoke(config);
            iris.getMethod("reload").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Glazuli.LOGGER.warn("[autoshot] could not disable shaders", e);
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
            Glazuli.LOGGER.info("[autoshot] switched shader pack {} -> {}", currentName, next);
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException e) {
            Glazuli.LOGGER.warn("[autoshot] could not switch shader pack", e);
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
            Glazuli.LOGGER.warn("[autoshot] could not open {}", screenClass, e);
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
            Glazuli.LOGGER.warn("[autoshot] could not open JEI recipes", e);
        }
    }

    static void enterWorld() {
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
    // Mid-animation, the last frame, and the static logo that replaces it (must match it pixel for pixel).
    private static final double[] LOGO_SHOT_TIMES = {1.0, 2.85, 3.05};
    private static final double LOGO_STATIC_FROM = 3.0;
    private static final double LOGO_FRAMES_END = 2.73;
    private static final boolean[] logoTaken = new boolean[LOGO_SHOT_TIMES.length];
    private static double logoLastFrame = Double.NaN;
    private static double logoMaxGap;
    private static int logoFrames;
    private static int logoSlowFrames;
    private static boolean logoLogged;

    /** Autoshot only: the logo animation shows vanilla's static logo instead of its last frame, for comparison. */
    public static boolean forceStaticLogo(double elapsed) {
        return AUTOSHOT && elapsed >= LOGO_STATIC_FROM;
    }

    /** Frames of the Mojang logo animation, by seconds since it started; logs frame pacing (screenshot frames excluded). */
    public static void onLogoAnimationFrame(double elapsed) {
        if (!AUTOSHOT) {
            return;
        }
        if (!Double.isNaN(logoLastFrame) && elapsed < LOGO_FRAMES_END) {
            double gap = elapsed - logoLastFrame;
            logoMaxGap = Math.max(logoMaxGap, gap);
            logoFrames++;
            if (gap > 0.012) {
                logoSlowFrames++;
            }
        } else if (elapsed >= LOGO_FRAMES_END && !logoLogged) {
            logoLogged = true;
            Glazuli.LOGGER.info("[autoshot] logo animation: {} frames ({} fps), {} over 12 ms, worst gap {} ms", logoFrames,
                    Math.round(logoFrames / LOGO_FRAMES_END), logoSlowFrames, Math.round(logoMaxGap * 1000));
        }
        logoLastFrame = elapsed;
        for (int i = 0; i < LOGO_SHOT_TIMES.length; i++) {
            if (!logoTaken[i] && elapsed >= LOGO_SHOT_TIMES[i]) {
                logoTaken[i] = true;
                logoLastFrame = Double.NaN;
                grab("glazuli_autoshot_boot_logo_" + i + ".png");
                return;
            }
        }
    }

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
                CaveBackground.rememberSelection(false);
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
        Glazuli.LOGGER.info("[autoshot] {} <- screen {}", fileName, mc.screen == null ? null : mc.screen.getClass().getName());
        Screenshot.grab(mc.gameDirectory, fileName, mc.getMainRenderTarget(),
                msg -> Glazuli.LOGGER.info("[autoshot] {}", msg.getString()));
    }

    private static boolean selected(Shot shot) {
        if (ONLY.isBlank()) {
            return true;
        }
        for (String part : ONLY.split(",")) {
            if (!part.isBlank() && shot.fileName().contains(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private static void startShot(int index) {
        while (index < SHOTS.length && !selected(SHOTS[index])) {
            index++;
        }
        shot = index;
        shotStart = AnimClock.now();
        grabbed = false;
        if (index < SHOTS.length) {
            SHOTS[index].setup().run();
        } else {
            GlazuliConfig.ENABLE_GLASS.set(true);
            if (AUTOSHOT_EXIT) {
                Glazuli.LOGGER.info("[autoshot] done, stopping client");
                Minecraft.getInstance().stop();
            }
        }
    }

    /**
     * {@code ready}, if set, triggers the grab early; {@code settleSeconds} is then only a timeout.
     * {@code waitForOverlay}: only start once no loading overlay is showing.
     */
    /**
     * The scene (already showing) with the real-time clock pinned to {@code time}, a few seconds later so the light map
     * and a shader pack's exposure have caught up; the next scene shot follows the real clock again.
     */
    private static Shot clockShot(String id, String suffix, LocalTime time) {
        String clock = String.format("_%02d%02d", time.getHour(), time.getMinute());
        return new Shot("glazuli_autoshot_scene_" + id + suffix + clock + ".png", 3.0, () -> {
            CaveBackground.devSpot(null);
            SceneClock.override(time);
        });
    }

    private static Shot sceneShot(String id, String suffix) {
        return new Shot("glazuli_autoshot_scene_" + id.replace(':', '_') + suffix + ".png", 2.0 * SCENE_TIMEOUT, () -> {
            SceneClock.override(null);
            CaveBackground.devSpot(null);
            CaveBackground.select(id, false);
            if (!(Minecraft.getInstance().screen instanceof BareTitle)) {
                Minecraft.getInstance().setScreen(new BareTitle());
            }
        }, () -> CaveBackground.isShowing(id), true);
    }

    /** The showing scene from another menu's camera spot (no menu drawn), once the camera has flown there. */
    private static Shot spotShot(String id, String spot) {
        return new Shot("glazuli_autoshot_scene_" + id + "_spot_" + spot.toLowerCase(java.util.Locale.ROOT) + ".png", 4.3,
                () -> CaveBackground.devSpot(spot), null, false);
    }

    /** The title screen's camera spot with only the scene drawn, so scene shots show the whole picture. */
    private static final class BareTitle extends TitleScreen {
        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            this.renderPanorama(guiGraphics, partialTick);
        }
    }

    private record Shot(String fileName, double settleSeconds, Runnable setup, @Nullable BooleanSupplier ready, boolean waitForOverlay) {
        Shot(String fileName, double settleSeconds, Runnable setup) {
            this(fileName, settleSeconds, setup, null, true);
        }
    }

    private record BootShot(String fileName, IntroState.Phase phase, double at) {
    }
}
