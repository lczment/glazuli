package com.lczme.liquidglass.cave;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.mixin.CameraAccessor;
import com.lczme.liquidglass.mixin.ClientPacketListenerAccessor;
import com.lczme.liquidglass.mixin.GameRendererAccessor;
import com.lczme.liquidglass.mixin.LevelRendererAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The menu cave as a real (display-only) client level, drawn by the actual world renderer so a loaded Iris shader pack
 * shades it like a world. There is no server: the level gets vanilla's built-in registries, the cave voxels as chunk
 * packets with precomputed light, and a stand-in player as camera. {@link Minecraft#level} and {@link Minecraft#player}
 * only point at it while it is being drawn, so nothing else ever ticks or sees it.
 */
final class ShaderCave {
    /** The cave voxels sit at the world origin; stone fills the rest of these chunks and caps the top against sky light. */
    private static final int MIN_Y = -16;
    private static final int MAX_Y = CaveWorld.SIZE_Y + 8;
    private static final int CHUNKS_X = (CaveWorld.SIZE_X + 15) >> 4;
    private static final int CHUNKS_Z = (CaveWorld.SIZE_Z + 15) >> 4;
    /**
     * Solid chunks around the cave: the inner ring hides the void past the cave's edge, the outer one gives the inner
     * ring the loaded neighbours Sodium needs before it meshes a chunk.
     */
    private static final int RING = 2;
    private static final int SPAN_X = CHUNKS_X + 2 * RING;
    private static final int SPAN_Z = CHUNKS_Z + 2 * RING;
    private static final int VIEW_DISTANCE = Math.max(SPAN_X, SPAN_Z) / 2 + 2;
    private static final float EYE_HEIGHT = 1.62F;
    private static final BlockState FILL = Blocks.STONE.defaultBlockState();

    @Nullable
    private static CompletableFuture<RegistryAccess.Frozen> registries;
    @Nullable
    private static ClientLevel level;
    @Nullable
    private static LocalPlayer player;
    private static boolean failed;
    private static boolean drawnThisFrame;
    private static boolean loggedRepeat;
    @Nullable
    private static Object irisApi;
    @Nullable
    private static Method shaderPackInUse;

    private ShaderCave() {
    }

    /** Only worth the cost while a shader pack is active; otherwise the plain mesh looks the same. */
    static boolean wanted() {
        if (failed) {
            return false;
        }
        if (Boolean.getBoolean("liquidglass.shaderCave")) {
            return true;
        }
        if (!ModList.get().isLoaded("iris")) {
            return false;
        }
        try {
            if (shaderPackInUse == null) {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                shaderPackInUse = api.getMethod("isShaderPackInUse");
            }
            return (Boolean) shaderPackInUse.invoke(irisApi);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("Iris API unavailable", e);
            return false;
        }
    }

    /**
     * Draws the cave through the world renderer from {@code camera}; false if not (yet) possible, in which case the
     * caller draws the plain mesh.
     */
    static boolean render(Minecraft minecraft, CaveWorld world, CaveCamera camera) {
        if (!wanted()) {
            return false;
        }
        try {
            if (level == null && !build(minecraft, world)) {
                return false;
            }
            if (drawnThisFrame) {
                if (!loggedRepeat) {
                    loggedRepeat = true;
                    LiquidGlass.LOGGER.info("Shader cave asked to render twice in one frame; reusing the first image");
                }
                return true;
            }
            drawnThisFrame = true;
            draw(minecraft, camera);
            return true;
        } catch (RuntimeException | LinkageError e) {
            fail("Shader cave failed, falling back to the plain cave", e);
            return false;
        }
    }

    /**
     * Shader packs advance their temporal history (previous camera, previous frame) on every world render, so a second
     * render in the same frame would make them reproject from a "previous" view identical to the current one.
     */
    static void newFrame() {
        drawnThisFrame = false;
    }

    private static void fail(String message, Throwable e) {
        failed = true;
        LiquidGlass.LOGGER.error(message, e);
    }

    /** Vanilla's worldgen registries (biomes, dimension types, damage types) from the built-in data pack, off-thread. */
    private static RegistryAccess.Frozen loadRegistries() {
        PackRepository packs = ServerPacksSource.createVanillaTrustedRepository();
        packs.reload();
        packs.setSelected(List.of("vanilla"));
        try (CloseableResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, packs.openAllSelected())) {
            LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
            RegistryAccess.Frozen worldgen = RegistryDataLoader.load(resources,
                    layers.getAccessForLoading(RegistryLayer.WORLDGEN), RegistryDataLoader.WORLDGEN_REGISTRIES);
            return layers.replaceFrom(RegistryLayer.WORLDGEN, worldgen).compositeAccess();
        }
    }

    private static boolean build(Minecraft minecraft, CaveWorld world) {
        if (registries == null) {
            registries = CompletableFuture.supplyAsync(ShaderCave::loadRegistries, Util.backgroundExecutor());
        }
        if (!registries.isDone()) {
            return false;
        }
        RegistryAccess.Frozen access = registries.join();
        long start = Util.getMillis();

        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        CommonListenerCookie cookie = new CommonListenerCookie(minecraft.getGameProfile(),
                new WorldSessionTelemetryManager(TelemetryEventSender.DISABLED, false, Duration.ZERO, null),
                access, FeatureFlags.DEFAULT_FLAGS, "liquidglass", null, null, Map.of(), null, false, Map.of(), ServerLinks.EMPTY);
        ClientPacketListener listener = new ClientPacketListener(minecraft, connection, cookie);
        ClientLevel.ClientLevelData data = new ClientLevel.ClientLevelData(Difficulty.PEACEFUL, false, false);
        ClientLevel cave = new ClientLevel(listener, data, Level.OVERWORLD,
                access.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD),
                VIEW_DISTANCE, VIEW_DISTANCE, minecraft::getProfiler, minecraft.levelRenderer, false, 0L);
        ((ClientPacketListenerAccessor) listener).liquidglass$setLevel(cave);
        cave.setDayTime(6000L);

        LevelChunk[] chunks = new LevelChunk[SPAN_X * SPAN_Z];
        for (int cx = -RING; cx < CHUNKS_X + RING; cx++) {
            for (int cz = -RING; cz < CHUNKS_Z + RING; cz++) {
                chunks[index(cx, cz)] = fill(cave, world, new ChunkPos(cx, cz));
            }
        }
        LevelLightEngine light = light(cave, chunks);
        cave.getChunkSource().updateViewCenter(CHUNKS_X / 2, CHUNKS_Z / 2);
        minecraft.levelRenderer.setLevel(cave);
        for (LevelChunk chunk : chunks) {
            listener.handleLevelChunkWithLight(new ClientboundLevelChunkWithLightPacket(chunk, light, null, null));
        }

        LocalPlayer stand = new LocalPlayer(minecraft, cave, listener, new StatsCounter(), new ClientRecipeBook(), false, false);
        level = cave;
        player = stand;
        LiquidGlass.LOGGER.info("Shader cave level built in {} ms", Util.getMillis() - start);
        return true;
    }

    private static int index(int cx, int cz) {
        return (cx + RING) * SPAN_Z + cz + RING;
    }

    private static LevelChunk fill(ClientLevel cave, CaveWorld world, ChunkPos pos) {
        LevelChunk chunk = new LevelChunk(cave, pos);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = pos.getMinBlockX() + lx;
                int z = pos.getMinBlockZ() + lz;
                for (int y = MIN_Y; y < MAX_Y; y++) {
                    BlockState state = CaveWorld.inBounds(x, y, z) ? world.get(x, y, z) : FILL;
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    section.setBlockState(lx, y & 15, lz, state, false);
                }
            }
        }
        for (LevelChunkSection section : chunk.getSections()) {
            section.recalcBlockCounts();
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
        chunk.initializeLightSources();
        return chunk;
    }

    /** Lights the chunks the way the server does for new chunks, so the packets carry complete light data. */
    private static LevelLightEngine light(ClientLevel cave, LevelChunk[] chunks) {
        LevelLightEngine engine = new LevelLightEngine(new LightChunkGetter() {
            @Nullable
            @Override
            public LightChunk getChunkForLighting(int x, int z) {
                return x >= -RING && z >= -RING && x < CHUNKS_X + RING && z < CHUNKS_Z + RING ? chunks[index(x, z)] : null;
            }

            @Override
            public BlockGetter getLevel() {
                return cave;
            }
        }, true, cave.dimensionType().hasSkyLight());
        for (LevelChunk chunk : chunks) {
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                if (!sections[i].hasOnlyAir()) {
                    engine.updateSectionStatus(SectionPos.of(chunk.getPos(), cave.getSectionYFromSectionIndex(i)), false);
                }
            }
            engine.setLightEnabled(chunk.getPos(), true);
        }
        for (LevelChunk chunk : chunks) {
            engine.propagateLightSources(chunk.getPos());
        }
        engine.runLightUpdates();
        return engine;
    }

    /** The state a frame normally starts the world in; GUI drawing before us leaves blending and colour set. */
    private static void resetGlState() {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableCull();
        RenderSystem.disableScissor();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void draw(Minecraft minecraft, CaveCamera camera) {
        ClientLevel cave = level;
        LocalPlayer stand = player;
        if (((LevelRendererAccessor) minecraft.levelRenderer).liquidglass$level() != cave) {
            minecraft.levelRenderer.setLevel(cave);
        }
        Vector3f eye = camera.eye;
        float dx = camera.target.x - eye.x;
        float dy = camera.target.y - eye.y;
        float dz = camera.target.z - eye.z;
        float yaw = (float) Math.toDegrees(-Mth.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        stand.moveTo(eye.x, eye.y - EYE_HEIGHT, eye.z, yaw, pitch);
        stand.yHeadRot = yaw;
        stand.yHeadRotO = yaw;

        GameRenderer gameRenderer = minecraft.gameRenderer;
        ClientLevel previousLevel = minecraft.level;
        LocalPlayer previousPlayer = minecraft.player;
        Entity previousCamera = minecraft.cameraEntity;
        HitResult previousHit = minecraft.hitResult;
        Entity previousPick = minecraft.crosshairPickEntity;
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        CameraType cameraType = minecraft.options.getCameraType();
        GameRendererAccessor fov = (GameRendererAccessor) gameRenderer;
        float previousFov = fov.liquidglass$fov();
        float previousOldFov = fov.liquidglass$oldFov();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        try {
            minecraft.level = cave;
            minecraft.player = stand;
            minecraft.cameraEntity = stand;
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            CameraAccessor mainCamera = (CameraAccessor) gameRenderer.getMainCamera();
            mainCamera.liquidglass$setEyeHeight(EYE_HEIGHT);
            mainCamera.liquidglass$setEyeHeightOld(EYE_HEIGHT);
            fov.liquidglass$setFov(1.0F);
            fov.liquidglass$setOldFov(1.0F);
            gameRenderer.setRenderHand(false);
            gameRenderer.setRenderBlockOutline(false);
            gameRenderer.lightTexture().tick();
            resetGlState();
            gameRenderer.renderLevel(minecraft.getTimer());
        } finally {
            minecraft.level = previousLevel;
            minecraft.player = previousPlayer;
            minecraft.cameraEntity = previousCamera;
            minecraft.options.setCameraType(cameraType);
            fov.liquidglass$setFov(previousFov);
            fov.liquidglass$setOldFov(previousOldFov);
            minecraft.hitResult = previousHit;
            minecraft.crosshairPickEntity = previousPick;
            gameRenderer.setRenderHand(true);
            gameRenderer.setRenderBlockOutline(true);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
    }
}
