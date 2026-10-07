package com.lczme.liquidglass.cave;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** The menu scenes to choose from: the generated themes plus the scenes captured with {@code /menuscene save}. */
public final class MenuScenes {
    public static final String DEFAULT = CaveTheme.CAVE.id();
    private static final String CUSTOM = "custom:";
    private static final String EXTENSION = ".nbt";
    private static final long SEED = 0x4C47_4361_7665L;

    public record Entry(String id, Component name) {
    }

    private MenuScenes() {
    }

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(LiquidGlass.MODID).resolve("scenes");
    }

    public static String configured() {
        return LiquidGlassConfig.SPEC.isLoaded() ? LiquidGlassConfig.MENU_SCENE.get() : DEFAULT;
    }

    public static List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        for (CaveTheme theme : CaveTheme.values()) {
            entries.add(new Entry(theme.id(), theme.displayName()));
        }
        for (String id : BuiltScenes.ids()) {
            entries.add(new Entry(id, Component.translatable("liquidglass.scene." + id)));
        }
        Path dir = directory();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(EXTENSION))
                        .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(name -> entries.add(new Entry(CUSTOM + name, Component.literal(name))));
            } catch (IOException e) {
                LiquidGlass.LOGGER.warn("Couldn't list menu scenes in {}", dir, e);
            }
        }
        return entries;
    }

    /** Generates or reads the scene; off-thread. Unknown or unreadable scenes fall back to the default cave. */
    static CaveWorld load(String id) {
        if (id.startsWith(CUSTOM)) {
            Path file = directory().resolve(id.substring(CUSTOM.length()) + EXTENSION);
            try {
                return SceneFile.read(file);
            } catch (IOException | RuntimeException e) {
                LiquidGlass.LOGGER.error("Couldn't load menu scene {}, using the default cave", file, e);
            }
        }
        if (BuiltScenes.has(id)) {
            try {
                return BuiltScenes.build(id, SEED);
            } catch (IOException | RuntimeException e) {
                LiquidGlass.LOGGER.error("Couldn't build menu scene {}, using the default cave", id, e);
            }
        }
        CaveTheme theme = CaveTheme.byId(id);
        return CaveGenerator.generate(SEED, theme != null ? theme : CaveTheme.CAVE);
    }

    private static String fileName(String name) {
        String clean = name.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (clean.length() > 48) {
            clean = clean.substring(0, 48);
        }
        return clean.isBlank() || clean.startsWith(".") ? "scene" : clean;
    }

    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("menuscene")
                .then(Commands.literal("save")
                        .executes(context -> save(context, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> save(context, StringArgumentType.getString(context, "name"))))));
    }

    private static int save(CommandContext<CommandSourceStack> context, String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return 0;
        }
        String file = fileName(name);
        CompoundTag tag = SceneFile.capture(minecraft);
        Path path = directory().resolve(file + EXTENSION);
        CommandSourceStack source = context.getSource();
        Util.ioPool().execute(() -> {
            try {
                SceneFile.write(tag, path);
                minecraft.execute(() -> {
                    CaveBackground.select(CUSTOM + file, true);
                    source.sendSuccess(() -> Component.translatable("liquidglass.scene.saved", file), false);
                });
            } catch (IOException e) {
                LiquidGlass.LOGGER.error("Couldn't save menu scene {}", path, e);
                minecraft.execute(() -> source.sendFailure(Component.translatable("liquidglass.scene.save_failed", e.getMessage())));
            }
        });
        return 1;
    }
}
