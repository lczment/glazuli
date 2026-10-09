package com.lczme.glazuli.title;

import com.lczme.glazuli.mixin.MinecraftAccessor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.server.WorldLoader;

/**
 * Loads a world's datapacks off the render thread. Vanilla reads, parses and applies every recipe, advancement and
 * tag on the render thread and blocks the game until it is done, so with a big modpack the menu freezes for seconds
 * on "Create New World" and on opening a world. Here the whole load, apply steps included, runs on one worker thread
 * (as a /reload runs on the integrated server's thread) while the loading screen keeps drawing.
 */
public final class WorldDataLoad {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Glazuli world data");
        thread.setDaemon(true);
        thread.setContextClassLoader(WorldDataLoad.class.getClassLoader());
        return thread;
    });

    private WorldDataLoad() {
    }

    public static <D, R> CompletableFuture<R> load(WorldLoader.InitConfig config, WorldLoader.WorldDataSupplier<D> data,
            WorldLoader.ResultFactory<D, R> result) {
        return CompletableFuture.supplyAsync(() -> WorldLoader.load(config, data, result, Util.backgroundExecutor(), WORKER), WORKER)
            .thenCompose(future -> future);
    }

    /** Waits like {@code Minecraft.doWorldLoad} does for the server: drawing frames, without ticking the game. */
    public static void await(Minecraft minecraft, BooleanSupplier done) {
        while (!done.getAsBoolean()) {
            ((MinecraftAccessor) minecraft).glazuli$runTick(false);
        }
    }
}
