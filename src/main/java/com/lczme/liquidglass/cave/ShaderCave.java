package com.lczme.liquidglass.cave;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.mixin.CameraAccessor;
import com.lczme.liquidglass.mixin.ClientPacketListenerAccessor;
import com.lczme.liquidglass.mixin.GameRendererAccessor;
import com.lczme.liquidglass.mixin.LevelRendererAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.ServerLinks;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The menu cave as a real (display-only) client level, drawn by the actual world renderer so a loaded Iris shader pack
 * shades it like a world. There is no server: the level gets vanilla's built-in registries, the cave voxels as chunk
 * packets with precomputed light, and a stand-in player as camera. {@link Minecraft#level} and {@link Minecraft#player}
 * only point at it while it is being drawn, so nothing else ever ticks or sees it.
 */
public final class ShaderCave {
    /**
     * The scene's voxels sit at the world origin; {@link CaveWorld#get} fills the rest of these chunks (a closed cave's
     * stone also caps the top against sky light).
     */
    private static final int MIN_Y = -16;
    /**
     * Chunks around the scene: the inner ring hides the void past its edge, the outer one gives the inner ring the
     * loaded neighbours Sodium needs before it meshes a chunk.
     */
    private static final int RING = 2;
    private static final float EYE_HEIGHT = 1.62F;

    private record Span(int chunksX, int chunksZ, int maxY) {
        static Span of(CaveWorld world) {
            return new Span((world.sizeX + 15) >> 4, (world.sizeZ + 15) >> 4, world.sizeY + 8);
        }

        int spanX() {
            return chunksX + 2 * RING;
        }

        int spanZ() {
            return chunksZ + 2 * RING;
        }

        int viewDistance() {
            return Math.max(spanX(), spanZ()) / 2 + 2;
        }

        int index(int cx, int cz) {
            return (cx + RING) * spanZ() + cz + RING;
        }

        boolean contains(int cx, int cz) {
            return cx >= -RING && cz >= -RING && cx < chunksX + RING && cz < chunksZ + RING;
        }
    }

    /** Frames rendered before the cave counts as warmed up (shader programs compiled, history buffers filled). */
    private static final int WARM_FRAMES = 8;
    /**
     * Every section must stay compiled this long: the section graph is built over several frames and light updates
     * re-mesh sections, so a single "all rendered" frame can still be followed by chunks popping in.
     */
    private static final double SETTLE_SECONDS = 0.75;
    private static final long TICK_MILLIS = 50L;

    @Nullable
    private static ClientLevel pendingLevel;
    @Nullable
    private static ClientPacketListener pendingListener;
    // Render-stage listeners (Create's track outline, for one) assume a game mode whenever there is a level.
    private static MultiPlayerGameMode gameMode;
    @Nullable
    private static CompletableFuture<List<ClientboundLevelChunkWithLightPacket>> chunkData;
    @Nullable
    private static ClientLevel level;
    @Nullable
    private static LocalPlayer player;
    /** The scene {@link #level} (or the pending one) shows; another scene rebuilds the level. */
    @Nullable
    private static CaveWorld builtFor;
    private static boolean failed;
    private static boolean drawnThisFrame;
    private static boolean loggedWarm;
    private static int warmFrames;
    private static double settledSince = Double.NaN;
    private static long lastLightTick;
    @Nullable
    private static Object lastPipeline;
    @Nullable
    private static Method currentPack;
    @Nullable
    private static Object pipelineManager;
    @Nullable
    private static Method pipelineNullable;

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
        // "Pack loaded", not IrisApi#isShaderPackInUse: that needs a pipeline, which on the title screen only our own
        // world render creates, so after a pack switch it would stay false.
        try {
            if (currentPack == null) {
                currentPack = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPack");
            }
            return ((java.util.Optional<?>) currentPack.invoke(null)).isPresent();
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("Iris API unavailable", e);
            return false;
        }
    }

    /**
     * Scenes with block entities (beds, chests, Create's machines) always go through the world renderer: the plain mesh
     * only has block models.
     */
    static boolean wanted(CaveWorld world) {
        return world.needsWorld() ? !failed : wanted();
    }

    /** Whether {@code level} is the menu scene's (Flywheel must not instance its block entities: nothing ticks it). */
    public static boolean isSceneLevel(@Nullable Object level) {
        return level != null && (level == ShaderCave.level || level == pendingLevel);
    }

    /**
     * Draws the cave through the world renderer from {@code camera}; false if not (yet) possible, in which case the
     * caller draws the plain mesh.
     */
    static boolean render(Minecraft minecraft, CaveWorld world, CaveCamera camera) {
        if (!wanted(world)) {
            return false;
        }
        try {
            if (builtFor != world) {
                reset();
                builtFor = world;
            }
            if (level == null && !build(minecraft, world)) {
                return false;
            }
            if (drawnThisFrame) {
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
     * True once the level is built, the shader pack's programs are compiled and every chunk is meshed, so showing the
     * cave won't stall a frame (or there is nothing to warm up).
     */
    static boolean isWarm(Minecraft minecraft, CaveWorld world) {
        if (!wanted(world)) {
            return true;
        }
        boolean warm = level != null && builtFor == world && !Double.isNaN(settledSince) && AnimClock.now() - settledSince >= SETTLE_SECONDS;
        if (warm && !loggedWarm) {
            loggedWarm = true;
            LiquidGlass.LOGGER.info("Shader cave warmed up after {} frames", warmFrames);
        }
        return warm;
    }

    /**
     * Shader packs advance their temporal history (previous camera, previous frame) on every world render, so a second
     * render in the same frame would make them reproject from a "previous" view identical to the current one.
     */
    static void newFrame() {
        drawnThisFrame = false;
    }

    /** Drops the level of the previous scene; the world renderer switches to the next one once it is delivered. */
    private static void reset() {
        level = null;
        player = null;
        gameMode = null;
        pendingLevel = null;
        pendingListener = null;
        chunkData = null;
        warmFrames = 0;
        settledSince = Double.NaN;
        loggedWarm = false;
    }

    private static void fail(String message, Throwable e) {
        failed = true;
        LiquidGlass.LOGGER.error(message, e);
    }

    private static boolean build(Minecraft minecraft, CaveWorld world) {
        CompletableFuture<RegistryAccess.Frozen> registries = VanillaData.registries();
        if (!registries.isDone()) {
            return false;
        }
        Span span = Span.of(world);
        if (pendingLevel == null) {
            RegistryAccess.Frozen access = registries.join();
            Connection connection = new Connection(PacketFlow.CLIENTBOUND);
            CommonListenerCookie cookie = new CommonListenerCookie(minecraft.getGameProfile(),
                    new WorldSessionTelemetryManager(TelemetryEventSender.DISABLED, false, Duration.ZERO, null),
                    access, FeatureFlags.DEFAULT_FLAGS, "liquidglass", null, null, Map.of(), null, false, Map.of(), ServerLinks.EMPTY);
            ClientPacketListener listener = new ClientPacketListener(minecraft, connection, cookie);
            ClientLevel.ClientLevelData data = new ClientLevel.ClientLevelData(Difficulty.PEACEFUL, false, false);
            SceneLook.Dimension dimension = world.look().dimension();
            ClientLevel cave = new ClientLevel(listener, data, dimension.level,
                    access.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(dimension.type),
                    span.viewDistance(), span.viewDistance(), minecraft::getProfiler, minecraft.levelRenderer, false, 0L);
            ((ClientPacketListenerAccessor) listener).liquidglass$setLevel(cave);
            cave.setDayTime(world.look().dayTime());
            pendingLevel = cave;
            pendingListener = listener;
            chunkData = CompletableFuture.supplyAsync(() -> chunkPackets(cave, world, span), Util.backgroundExecutor());
            return false;
        }
        if (!chunkData.isDone()) {
            return false;
        }
        long start = Util.getMillis();
        ClientLevel cave = pendingLevel;
        ClientPacketListener listener = pendingListener;
        cave.getChunkSource().updateViewCenter(span.chunksX() / 2, span.chunksZ() / 2);
        minecraft.levelRenderer.setLevel(cave);
        for (ClientboundLevelChunkWithLightPacket packet : chunkData.join()) {
            listener.handleLevelChunkWithLight(packet);
        }
        LocalPlayer stand = new LocalPlayer(minecraft, cave, listener, new StatsCounter(), new ClientRecipeBook(), false, false);
        level = cave;
        player = stand;
        gameMode = new MultiPlayerGameMode(minecraft, listener);
        chunkData = null;
        LiquidGlass.LOGGER.info("Shader cave level delivered in {} ms", Util.getMillis() - start);
        return true;
    }

    /** Fills, lights and serializes the chunks; touches nothing but the new chunks, so it runs off-thread. */
    private static List<ClientboundLevelChunkWithLightPacket> chunkPackets(ClientLevel cave, CaveWorld world, Span span) {
        long start = Util.getMillis();
        LevelChunk[] chunks = new LevelChunk[span.spanX() * span.spanZ()];
        for (int cx = -RING; cx < span.chunksX() + RING; cx++) {
            for (int cz = -RING; cz < span.chunksZ() + RING; cz++) {
                chunks[span.index(cx, cz)] = fill(cave, world, new ChunkPos(cx, cz), span.maxY());
            }
        }
        LevelLightEngine light = light(cave, chunks, span);
        List<ClientboundLevelChunkWithLightPacket> packets = new java.util.ArrayList<>(chunks.length);
        for (LevelChunk chunk : chunks) {
            packets.add(new ClientboundLevelChunkWithLightPacket(chunk, light, null, null));
        }
        LiquidGlass.LOGGER.info("Shader cave chunks prepared in {} ms", Util.getMillis() - start);
        return packets;
    }

    /** Iris' current world pipeline; a new object after every pack switch or option change. */
    @Nullable
    private static Object irisPipeline() {
        try {
            if (pipelineNullable == null) {
                pipelineManager = Class.forName("net.irisshaders.iris.Iris").getMethod("getPipelineManager").invoke(null);
                pipelineNullable = pipelineManager.getClass().getMethod("getPipelineNullable");
            }
            return pipelineNullable.invoke(pipelineManager);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static LevelChunk fill(ClientLevel cave, CaveWorld world, ChunkPos pos, int maxY) {
        LevelChunk chunk = new LevelChunk(cave, pos);
        int minY = Math.max(MIN_Y, cave.getMinBuildHeight());
        maxY = Math.min(maxY, cave.getMaxBuildHeight());
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = pos.getMinBlockX() + lx;
                int z = pos.getMinBlockZ() + lz;
                for (int y = minY; y < maxY; y++) {
                    BlockState state = world.get(x, y, z);
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
        world.blockEntities.forEach((blockPos, tag) -> {
            if (blockPos.getX() >> 4 == pos.x && blockPos.getZ() >> 4 == pos.z) {
                BlockState state = chunk.getBlockState(blockPos);
                if (state.hasBlockEntity()) {
                    try {
                        BlockEntity blockEntity = BlockEntity.loadStatic(blockPos, state, tag, cave.registryAccess());
                        if (blockEntity != null) {
                            chunk.setBlockEntity(blockEntity);
                        }
                    } catch (RuntimeException e) {
                        LiquidGlass.LOGGER.warn("Menu scene block entity at {} failed to load", blockPos, e);
                    }
                }
            }
        });
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
        chunk.initializeLightSources();
        return chunk;
    }

    /** Lights the chunks the way the server does for new chunks, so the packets carry complete light data. */
    private static LevelLightEngine light(ClientLevel cave, LevelChunk[] chunks, Span span) {
        LevelLightEngine engine = new LevelLightEngine(new LightChunkGetter() {
            @Nullable
            @Override
            public LightChunk getChunkForLighting(int x, int z) {
                return span.contains(x, z) ? chunks[span.index(x, z)] : null;
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
        MultiPlayerGameMode previousGameMode = minecraft.gameMode;
        // Screens may draw into their own framebuffer (Catnip's do, for stencils) and copy it over the main one after.
        int screenTarget = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
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
            minecraft.gameMode = gameMode;
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            CameraAccessor mainCamera = (CameraAccessor) gameRenderer.getMainCamera();
            mainCamera.liquidglass$setEyeHeight(EYE_HEIGHT);
            mainCamera.liquidglass$setEyeHeightOld(EYE_HEIGHT);
            fov.liquidglass$setFov(1.0F);
            fov.liquidglass$setOldFov(1.0F);
            gameRenderer.setRenderHand(false);
            gameRenderer.setRenderBlockOutline(false);
            long now = Util.getMillis();
            if (now - lastLightTick >= TICK_MILLIS) {
                lastLightTick = now;
                gameRenderer.lightTexture().tick();
                // Nothing ticks this level: advance the clocks block entity animations read (banners, Create's gears).
                cave.setGameTime(cave.getGameTime() + 1L);
                CreateCompat.tickAnimations();
            }
            resetGlState();
            gameRenderer.renderLevel(minecraft.getTimer());
            // Chunk meshes carry the pack's vertex format and block ids; Iris only rebuilds them when it creates a
            // pipeline while a level is showing, which on the title screen it never sees.
            Object pipeline = irisPipeline();
            if (pipeline != lastPipeline) {
                if (lastPipeline != null) {
                    minecraft.levelRenderer.allChanged();
                    warmFrames = 0;
                }
                lastPipeline = pipeline;
            }
            warmFrames++;
            if (!minecraft.levelRenderer.hasRenderedAllSections() || warmFrames < WARM_FRAMES) {
                settledSince = Double.NaN;
            } else if (Double.isNaN(settledSince)) {
                settledSince = AnimClock.now();
            }
        } finally {
            minecraft.level = previousLevel;
            minecraft.player = previousPlayer;
            minecraft.cameraEntity = previousCamera;
            minecraft.gameMode = previousGameMode;
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
            RenderTarget main = minecraft.getMainRenderTarget();
            main.bindWrite(true);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            if (screenTarget != main.frameBufferId) {
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, screenTarget);
                GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                        GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, screenTarget);
            }
        }
    }
}
