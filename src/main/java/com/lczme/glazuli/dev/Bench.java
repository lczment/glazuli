package com.lczme.glazuli.dev;

import com.lczme.glazuli.Glazuli;
import com.lczme.glazuli.cave.CaveBackground;
import com.lczme.glazuli.config.GlazuliConfig;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Development-only {@code -Dglazuli.bench=true} run: measures frame times and the mod's own CPU and GPU time in a
 * fixed list of menus and in-world screens, appends the results to {@code run/glazuli_bench.txt}, then exits.
 */
public final class Bench {
    private static final String ONLY = System.getProperty("glazuli.bench.only", "");
    private static final String LABEL = System.getProperty("glazuli.bench.label", "");
    private static final double WARMUP = 2.0;
    private static final double MEASURE = 6.0;
    private static final double READY_TIMEOUT = 90.0;
    private static final int MAX_SPIKES = 4;

    private static final Scenario[] SCENARIOS = {
            menu("menu_title", () -> {
                CaveBackground.select("cave", false);
                show(new TitleScreen());
            }, () -> CaveBackground.isShowing("cave", 2.0)),
            menu("menu_options", () -> show(new OptionsScreen(new TitleScreen(), Minecraft.getInstance().options)), null),
            // Back and forth between two menus: the camera flight and the screen cross-fade.
            new Scenario("menu_switch", Bench::restoreConfig, null, t -> {
                Minecraft mc = Minecraft.getInstance();
                boolean options = ((int) (t / 0.6)) % 2 == 1;
                if (options != mc.screen instanceof OptionsScreen) {
                    show(options ? new OptionsScreen(new TitleScreen(), mc.options) : new TitleScreen());
                }
            }, WARMUP),
            menu("menu_title_noglass", () -> {
                GlazuliConfig.ENABLE_GLASS.set(false);
                show(new TitleScreen());
            }, null),
            menu("menu_title_noshader", () -> {
                GlazuliConfig.SHADER_SCENE.set(false);
                show(new TitleScreen());
            }, null),
            menu("menu_title_nocave", () -> {
                GlazuliConfig.CAVE_BACKGROUND.set(false);
                show(new TitleScreen());
            }, null),
            menu("menu_vanilla", () -> {
                GlazuliConfig.CAVE_BACKGROUND.set(false);
                GlazuliConfig.ENABLE_GLASS.set(false);
                GlazuliConfig.GLASS_BUTTONS.set(false);
                GlazuliConfig.CUSTOM_TITLE_SCREEN.set(false);
                show(new TitleScreen());
            }, null),
            sceneScenario("plains_village"),
            sceneScenario("create_factory"),
            sceneScenario("nether"),
            new Scenario("world_warmup", () -> {
                restoreConfig();
                CaveBackground.select("cave", false);
                Minecraft.getInstance().tell(DevHooks::enterWorld);
            }, () -> Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null, null, 15.0),
            world("world_hud", true, () -> show(null)),
            world("world_hud_vanilla", false, () -> show(null)),
            world("world_hud_nofont", true, () -> {
                GlazuliConfig.CUSTOM_FONT.set(false);
                show(null);
            }),
            world("world_hud_noglass", true, () -> {
                GlazuliConfig.ENABLE_GLASS.set(false);
                show(null);
            }),
            world("world_inventory", true, () -> show(new InventoryScreen(Minecraft.getInstance().player))),
            world("world_inventory_vanilla", false, () -> show(new InventoryScreen(Minecraft.getInstance().player))),
            world("world_chat", true, () -> {
                for (int i = 0; i < 12; i++) {
                    Minecraft.getInstance().gui.getChat().addMessage(Component.literal("Benchmark chat line " + i));
                }
                show(new ChatScreen(""));
            }),
            world("world_chat_vanilla", false, () -> show(new ChatScreen(""))),
            world("world_pause", true, () -> show(new PauseScreen(true))),
            world("world_pause_vanilla", false, () -> show(new PauseScreen(true))),
            world("world_hud_again", true, () -> show(null)),
            world("world_hud_vanilla_again", false, () -> show(null)),
            new Scenario("world_switch", () -> setInGame(true), null, t -> {
                Minecraft mc = Minecraft.getInstance();
                boolean open = ((int) (t / 0.5)) % 2 == 1;
                if (open != mc.screen instanceof InventoryScreen) {
                    show(open ? new InventoryScreen(mc.player) : null);
                }
            }, WARMUP),
    };

    private static int index = -1;
    private static double start;
    private static double measureStart = Double.NaN;
    private static long lastFrame;
    private static long frameStart;
    private static final long[] intervals = new long[8192];
    private static final long[] work = new long[8192];
    private static final List<String> spikes = new ArrayList<>();
    private static long median;
    private static long intervalMedian = Long.MAX_VALUE / 4;
    private static final List<String> intervalSpikes = new ArrayList<>();
    private static int lastSwitchFrame;
    private static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static long allocStart;
    private static long allocated;
    private static long gcCountStart;
    private static long gcMillisStart;
    private static long pendingSwitch;
    private static long worstSwitch;
    private static int frames;
    private static boolean origVsync;
    private static int origLimit;

    private Bench() {
    }

    public static void register(IEventBus gameBus) {
        if (Perf.ON) {
            gameBus.addListener(EventPriority.HIGHEST, Bench::onFramePre);
            gameBus.addListener(EventPriority.LOWEST, Bench::onFramePost);
            gameBus.addListener(EventPriority.HIGHEST, (ScreenEvent.Render.Pre e) -> Perf.begin(Perf.SCREEN));
            gameBus.addListener(EventPriority.LOWEST, (ScreenEvent.Render.Post e) -> Perf.end(Perf.SCREEN));
            gameBus.addListener(EventPriority.HIGHEST, (RenderGuiEvent.Pre e) -> Perf.begin(Perf.HUD));
            gameBus.addListener(EventPriority.LOWEST, (RenderGuiEvent.Post e) -> Perf.end(Perf.HUD));
        }
    }

    private static double now() {
        return System.nanoTime() / 1.0E9;
    }

    private static void show(@Nullable Screen screen) {
        long t = System.nanoTime();
        Minecraft.getInstance().setScreen(screen);
        pendingSwitch += System.nanoTime() - t;
        lastSwitchFrame = frames;
    }

    private static long gcCount() {
        long n = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            n += Math.max(0, gc.getCollectionCount());
        }
        return n;
    }

    private static long gcMillis() {
        long n = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            n += Math.max(0, gc.getCollectionTime());
        }
        return n;
    }

    private static Scenario menu(String name, Runnable setup, @Nullable BooleanSupplier ready) {
        return new Scenario(name, () -> {
            restoreConfig();
            setup.run();
        }, ready, null, WARMUP);
    }

    private static Scenario world(String name, boolean glass, Runnable setup) {
        return new Scenario(name, () -> {
            setInGame(glass);
            setup.run();
        }, null, null, WARMUP);
    }

    private static void setInGame(boolean glass) {
        restoreConfig();
        GlazuliConfig.IN_GAME_UI.set(glass ? GlazuliConfig.InGameUi.SAME_AS_MENU : GlazuliConfig.InGameUi.VANILLA);
    }

    private static Scenario sceneScenario(String id) {
        return menu("scene_" + id, () -> {
            CaveBackground.select(id, false);
            show(new TitleScreen());
        }, () -> CaveBackground.isShowing(id, 1.0));
    }

    private static void restoreConfig() {
        GlazuliConfig.ENABLE_GLASS.set(true);
        GlazuliConfig.GLASS_BUTTONS.set(true);
        GlazuliConfig.CUSTOM_TITLE_SCREEN.set(true);
        GlazuliConfig.CAVE_BACKGROUND.set(true);
        GlazuliConfig.CUSTOM_FONT.set(true);
        GlazuliConfig.SHADER_SCENE.set(true);
        GlazuliConfig.IN_GAME_UI.set(GlazuliConfig.InGameUi.SAME_AS_MENU);
    }

    private static void onFramePre(RenderFrameEvent.Pre event) {
        long t = System.nanoTime();
        if (!Double.isNaN(measureStart) && lastFrame != 0 && frames < intervals.length) {
            long interval = t - lastFrame;
            intervals[frames] = interval;
            if (frames > 60 && intervalSpikes.size() < MAX_SPIKES && interval > 2 * intervalMedian && interval > 12_000_000L) {
                intervalSpikes.add(String.format("%.1fms(cpu %.1f, %d frames after setScreen)", ms(interval),
                        frames > 0 ? ms(work[frames - 1]) : 0.0, frames - lastSwitchFrame));
            }
        }
        lastFrame = t;
        frameStart = t;
        allocStart = THREADS.getCurrentThreadAllocatedBytes();
        Perf.startFrame();
        Perf.begin(Perf.FRAME);
    }

    private static void onFramePost(RenderFrameEvent.Post event) {
        Perf.end(Perf.FRAME);
        boolean measuring = !Double.isNaN(measureStart);
        if (measuring && frames < work.length) {
            long w = System.nanoTime() - frameStart;
            work[frames] = w;
            allocated += THREADS.getCurrentThreadAllocatedBytes() - allocStart;
            noteSpike(w);
            frames++;
            if (frames % 60 == 0) {
                long[] sorted = Arrays.copyOf(intervals, frames);
                Arrays.sort(sorted);
                intervalMedian = sorted[frames / 2];
            }
        }
        worstSwitch = Math.max(worstSwitch, pendingSwitch);
        pendingSwitch = 0;
        Perf.endFrame(measuring);
        Minecraft mc = Minecraft.getInstance();
        if (index < 0) {
            if (mc.getOverlay() == null && mc.screen != null) {
                CaveBackground.rememberSelection(false);
                origVsync = mc.options.enableVsync().get();
                origLimit = mc.options.framerateLimit().get();
                mc.options.enableVsync().set(false);
                mc.getWindow().updateVsync(false);
                mc.options.framerateLimit().set(260);
                mc.getWindow().setFramerateLimit(260);
                begin(0);
            }
            return;
        }
        if (index >= SCENARIOS.length) {
            return;
        }
        Scenario s = SCENARIOS[index];
        double t = now();
        if (s.tick() != null) {
            s.tick().accept(t - start);
        }
        if (!measuring) {
            boolean ready = s.ready() == null || s.ready().getAsBoolean();
            if (t - start >= READY_TIMEOUT || ready && t - start >= s.warmup()) {
                measureStart = t;
                frames = 0;
                lastFrame = 0;
                worstSwitch = 0;
                spikes.clear();
                intervalSpikes.clear();
                intervalMedian = Long.MAX_VALUE / 4;
                lastSwitchFrame = -1000;
                allocated = 0;
                gcCountStart = gcCount();
                gcMillisStart = gcMillis();
                Arrays.fill(intervals, 0L);
                Perf.reset();
            }
        } else if (t - measureStart >= MEASURE) {
            report(s, t - measureStart);
            begin(index + 1);
        }
    }

    /** Frames over twice the running median CPU time (after a second of samples), with where the time went. */
    private static void noteSpike(long w) {
        if (frames % 60 == 0 && frames > 0) {
            long[] sorted = Arrays.copyOf(work, frames);
            Arrays.sort(sorted);
            median = sorted[frames / 2];
        }
        if (frames < 60 || spikes.size() >= MAX_SPIKES || w < 2 * median || w < 4_000_000L) {
            return;
        }
        StringBuilder s = new StringBuilder(String.format("%.1fms@%d", ms(w), frames));
        for (int i = 1; i < Perf.NAMES.length; i++) {
            if (Perf.cpuFrame[i] > 300_000L) {
                s.append(String.format(" %s=%.1f", Perf.NAMES[i], ms(Perf.cpuFrame[i])));
            }
        }
        spikes.add(s.toString());
    }

    private static void begin(int i) {
        while (i < SCENARIOS.length && !selected(SCENARIOS[i])) {
            i++;
        }
        index = i;
        start = now();
        measureStart = Double.NaN;
        if (i < SCENARIOS.length) {
            Glazuli.LOGGER.info("[bench] start {}", SCENARIOS[i].name());
            SCENARIOS[i].setup().run();
        } else {
            restoreConfig();
            Minecraft mc = Minecraft.getInstance();
            mc.options.enableVsync().set(origVsync);
            mc.options.framerateLimit().set(origLimit);
            Glazuli.LOGGER.info("[bench] done, stopping client");
            mc.stop();
        }
    }

    private static boolean selected(Scenario s) {
        if (ONLY.isBlank()) {
            return true;
        }
        for (String part : ONLY.split(",")) {
            String p = part.trim();
            // The world scenarios need the one that enters the world.
            if (!p.isBlank() && (s.name().contains(p) || s.name().equals("world_warmup") && p.startsWith("world"))) {
                return true;
            }
        }
        return false;
    }

    private static void report(Scenario s, double seconds) {
        int n = Math.max(1, frames);
        long[] sorted = Arrays.copyOf(intervals, n);
        Arrays.sort(sorted);
        long[] sortedWork = Arrays.copyOf(work, n);
        Arrays.sort(sortedWork);
        StringBuilder line = new StringBuilder(String.format(
                "%-24s fps %6.1f | frame p50 %6.2f p99 %6.2f max %6.2f | cpu p50 %5.2f p99 %5.2f | setScreen max %5.2f",
                s.name(), n / seconds, ms(sorted[n / 2]), ms(sorted[Math.min(n - 1, (int) (n * 0.99))]), ms(sorted[n - 1]),
                ms(sortedWork[n / 2]), ms(sortedWork[Math.min(n - 1, (int) (n * 0.99))]), ms(worstSwitch)));
        line.append(String.format(" | alloc %.0f KB/frame | gc %d (%d ms)", allocated / 1024.0 / n, gcCount() - gcCountStart,
                gcMillis() - gcMillisStart));
        for (int i = 0; i < Perf.NAMES.length; i++) {
            if (Perf.calls[i] > 0) {
                line.append(String.format(" | %s cpu %.3f", Perf.NAMES[i], Perf.cpuTotal[i] / 1.0E6 / n));
                if (Perf.gpuTotal[i] > 0) {
                    line.append(String.format(" gpu %.3f", Perf.gpuTotal[i] / 1.0E6 / n));
                }
                line.append(String.format(" x%.1f", Perf.calls[i] / (double) n));
            }
        }
        if (!spikes.isEmpty()) {
            line.append(" | spikes ").append(String.join("; ", spikes));
        }
        if (!intervalSpikes.isEmpty()) {
            line.append(" | gaps ").append(String.join("; ", intervalSpikes));
        }
        Glazuli.LOGGER.info("[bench] {}", line);
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("glazuli_bench.txt");
            Files.writeString(file, (LABEL.isBlank() ? "" : "[" + LABEL + "] ") + line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Glazuli.LOGGER.warn("[bench] could not write results", e);
        }
    }

    private static double ms(long nanos) {
        return nanos / 1.0E6;
    }

    private record Scenario(String name, Runnable setup, @Nullable BooleanSupplier ready, @Nullable DoubleConsumer tick,
                            double warmup) {
    }
}
