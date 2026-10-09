package com.lczme.glazuli.title;

import com.lczme.glazuli.Glazuli;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import net.minecraft.client.server.LanServerPinger;

/**
 * Loads the classes behind the title menu's buttons on a background thread once the title screen is up. Opening one
 * of those screens for the first time otherwise loads, and with a modpack mixin-transforms, a few dozen classes on
 * the render thread: a 30-70 ms stall before the camera sets off for that menu's view.
 */
public final class MenuPreload {
    /** As in {@code LanServerDetection.LanServerDetector}. */
    private static final int LAN_PORT = 4445;
    private static final String[] CLASSES = {
            // Singleplayer
            "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen",
            "net.minecraft.client.gui.screens.worldselection.WorldSelectionList",
            "net.minecraft.client.gui.screens.worldselection.WorldSelectionList$Entry",
            "net.minecraft.client.gui.screens.worldselection.WorldSelectionList$WorldListEntry",
            "net.minecraft.client.gui.screens.worldselection.WorldSelectionList$LoadingHeader",
            "net.minecraft.client.gui.screens.worldselection.EditWorldScreen",
            "net.minecraft.client.gui.components.EditBox",
            "net.minecraft.client.gui.components.ObjectSelectionList",
            "net.minecraft.client.gui.components.ObjectSelectionList$Entry",
            "net.minecraft.client.gui.components.AbstractSelectionList$Entry",
            "net.minecraft.client.gui.components.AbstractSelectionList$TrackedList",
            "net.minecraft.client.gui.navigation.FocusNavigationEvent$InitialFocus",
            "net.minecraft.client.gui.ComponentPath",
            "net.minecraft.client.gui.ComponentPath$Leaf",
            "net.minecraft.client.gui.ComponentPath$Path",
            "net.minecraft.client.gui.screens.Screen$NarratableSearchResult",
            "net.minecraft.client.gui.screens.LoadingDotsText",
            "net.minecraft.client.gui.screens.AlertScreen",
            "net.minecraft.client.gui.screens.FaviconTexture",
            "net.minecraft.client.StringSplitter$WidthLimitedCharSink",
            "net.minecraft.world.level.levelgen.WorldOptions",
            "net.minecraft.world.level.storage.LevelStorageSource$LevelCandidates",
            "net.minecraft.world.level.storage.LevelVersion",
            "net.minecraft.world.level.storage.LevelSummary$BackupStatus",
            "net.minecraft.world.level.WorldDataConfiguration",
            "net.minecraft.world.level.LevelSettings",
            "net.minecraft.nbt.visitors.CollectToTag",
            "net.minecraft.nbt.visitors.SkipFields",
            "net.minecraft.nbt.visitors.FieldSelector",
            "net.minecraft.nbt.visitors.FieldTree",
            "net.minecraft.nbt.NbtIo$1",
            "net.minecraft.nbt.StreamTagVisitor$ValueResult",
            "net.minecraft.nbt.StreamTagVisitor$EntryResult",
            "net.minecraft.nbt.CompoundTag$2",
            "net.minecraft.nbt.ListTag$2",
            "net.minecraft.CharPredicate",
            // Multiplayer
            "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen",
            "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList",
            "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$Entry",
            "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$LANHeader",
            "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$NetworkServerEntry",
            "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry",
            "net.minecraft.client.gui.screens.EditServerScreen",
            "net.minecraft.client.gui.screens.DirectJoinServerScreen",
            "net.minecraft.client.multiplayer.ServerStatusPinger",
            "net.minecraft.client.multiplayer.ServerList",
            "net.minecraft.network.protocol.status.ClientStatusPacketListener",
            "net.minecraft.client.server.LanServerDetection",
            "net.minecraft.client.server.LanServerDetection$LanServerList",
            "net.minecraft.client.server.LanServerDetection$LanServerDetector",
            "net.minecraft.client.server.LanServerPinger",
            "net.minecraft.util.HttpUtil",
            "net.minecraft.client.gui.layouts.Layout",
            "net.minecraft.client.gui.layouts.AbstractLayout",
            "net.minecraft.client.gui.layouts.AbstractLayout$AbstractChildWrapper",
            "net.minecraft.client.gui.layouts.LinearLayout",
            "net.minecraft.client.gui.layouts.LinearLayout$Orientation",
            "net.minecraft.client.gui.layouts.GridLayout",
            "net.minecraft.client.gui.layouts.GridLayout$CellInhabitant",
            "net.minecraft.client.gui.layouts.GridLayout$RowHelper",
            "net.minecraft.client.gui.layouts.LayoutSettings",
            "net.minecraft.client.gui.layouts.LayoutSettings$LayoutSettingsImpl",
            "net.minecraft.client.gui.layouts.EqualSpacingLayout",
            "net.minecraft.client.gui.layouts.EqualSpacingLayout$Orientation",
            "net.minecraft.client.gui.layouts.EqualSpacingLayout$ChildContainer",
            "net.minecraft.client.gui.layouts.SpacerElement",
            "net.minecraft.client.gui.layouts.FrameLayout",
            "net.minecraft.client.gui.layouts.FrameLayout$ChildContainer",
            "net.minecraft.client.gui.layouts.HeaderAndFooterLayout",
            "com.mojang.math.Divisor",
            // Options
            "net.minecraft.client.gui.screens.options.OptionsScreen",
            "net.minecraft.client.gui.screens.options.OnlineOptionsScreen",
            "net.minecraft.client.gui.screens.options.SkinCustomizationScreen",
            "net.minecraft.client.gui.screens.options.controls.ControlsScreen",
            "net.minecraft.client.gui.screens.options.VideoSettingsScreen",
            "net.minecraft.client.gui.screens.options.SoundOptionsScreen",
            "net.minecraft.client.gui.screens.options.ChatOptionsScreen",
            "net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen",
            "net.minecraft.client.gui.screens.packs.PackSelectionScreen",
            // Mods
            "net.neoforged.neoforge.client.gui.ModListScreen",
            "net.neoforged.neoforge.client.gui.ModListScreen$1",
            "net.neoforged.neoforge.client.gui.ModListScreen$SortType",
            "net.neoforged.neoforge.client.gui.ModListScreen$InfoPanel",
            "net.neoforged.neoforge.client.gui.widget.ModListWidget",
            "net.neoforged.neoforge.client.gui.widget.ModListWidget$ModEntry",
            "net.neoforged.neoforge.client.gui.widget.ScrollPanel",
            "net.neoforged.neoforge.common.util.Size2i",
            "net.neoforged.fml.i18n.MavenVersionTranslator",
            "net.minecraft.client.StringSplitter$1",
            "net.minecraft.network.chat.FormattedText$3",
            "net.minecraft.network.chat.FormattedText$4",
    };
    private static boolean started;

    private MenuPreload() {
    }

    public static void start() {
        if (started) {
            return;
        }
        started = true;
        ClassLoader loader = MenuPreload.class.getClassLoader();
        Thread thread = new Thread(() -> {
            for (String name : CLASSES) {
                try {
                    // Loading (and transforming) only: static initialisers still run on first use, on the thread that uses them.
                    Class.forName(name, false, loader);
                } catch (ClassNotFoundException | LinkageError e) {
                    Glazuli.LOGGER.debug("Menu preload skipped {}", name, e);
                }
            }
            // The multiplayer screen listens for LAN games on a multicast socket; the first one sets up the
            // platform's datagram stack.
            try (MulticastSocket socket = new MulticastSocket(LAN_PORT)) {
                socket.joinGroup(InetAddress.getByName(LanServerPinger.MULTICAST_GROUP));
            } catch (IOException | SecurityException e) {
                Glazuli.LOGGER.debug("Menu preload: no LAN socket", e);
            }
        }, "Glazuli menu preload");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }
}
