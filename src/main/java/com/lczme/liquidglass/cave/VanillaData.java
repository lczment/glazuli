package com.lczme.liquidglass.cave;

import com.lczme.liquidglass.LiquidGlass;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;

/**
 * The vanilla data pack on the client: worldgen registries (biomes, dimension types, template pools, processors) for
 * the shader world and the structure scenes, and the structure templates themselves. Nothing here needs a server.
 */
final class VanillaData {
    @Nullable
    private static CompletableFuture<RegistryAccess.Frozen> registries;
    @Nullable
    private static CloseableResourceManager resources;
    @Nullable
    private static StructureTemplateManager templates;

    private VanillaData() {
    }

    private static synchronized CloseableResourceManager resources() {
        if (resources == null) {
            PackRepository packs = ServerPacksSource.createVanillaTrustedRepository();
            packs.reload();
            packs.setSelected(List.of("vanilla"));
            resources = new MultiPackResourceManager(PackType.SERVER_DATA, packs.openAllSelected());
        }
        return resources;
    }

    /** Loads (once) off-thread. */
    static synchronized CompletableFuture<RegistryAccess.Frozen> registries() {
        if (registries == null) {
            registries = CompletableFuture.supplyAsync(VanillaData::loadRegistries, Util.backgroundExecutor());
        }
        return registries;
    }

    private static RegistryAccess.Frozen loadRegistries() {
        LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
        RegistryAccess.Frozen worldgen = RegistryDataLoader.load(resources(),
                layers.getAccessForLoading(RegistryLayer.WORLDGEN), RegistryDataLoader.WORLDGEN_REGISTRIES);
        return layers.replaceFrom(RegistryLayer.WORLDGEN, worldgen).compositeAccess();
    }

    /** Vanilla's structure templates ({@code data/minecraft/structure}); blocks until made. */
    static synchronized StructureTemplateManager templates() throws IOException {
        if (templates == null) {
            // The manager also looks for templates saved in a world's "generated" folder; it gets an empty one.
            Path dir = Files.createTempDirectory(LiquidGlass.MODID + "-structures");
            try (LevelStorageSource.LevelStorageAccess access = LevelStorageSource.createDefault(dir).createAccess("menu")) {
                templates = new StructureTemplateManager(resources(), access, DataFixers.getDataFixer(), BuiltInRegistries.BLOCK.asLookup());
            }
        }
        return templates;
    }
}
