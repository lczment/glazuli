package com.lczme.glazuli.cave;

import com.lczme.glazuli.Glazuli;
import com.lczme.glazuli.anim.AnimClock;
import com.lczme.glazuli.config.GlazuliConfig;
import com.lczme.glazuli.intro.GlassLoadingOverlay;
import com.lczme.glazuli.intro.IntroState;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.lczme.glazuli.render.GlazuliShaders;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * LabyMod-style dynamic menu background: a real-time 3D cave replacing the vanilla panorama in every menu shown
 * without a world. The voxels are generated once at startup, the mesh is rebuilt off-thread after each resource reload
 * (block models may change), and the camera follows the open screen (see {@link CaveCamera}).
 */
public final class CaveBackground {
    private static final float FOV = (float) Math.toRadians(75.0);
    private static final float FADE_IN_SECONDS = 0.6F;
    /** Fade to dark before swapping in another scene. */
    private static final float SWITCH_OUT_SECONDS = 0.35F;
    /** A new scene is shown after this long even if the shader world hasn't warmed up. */
    private static final double WARM_WAIT_LIMIT = 10.0;
    /** The title menu stops waiting for the flight if the mesh isn't ready by then. */
    private static final double ENTRANCE_WAIT_LIMIT = 4.0;
    private static final int DARK = 0x050608;
    @Nullable
    private static CompletableFuture<CaveWorld> world;
    @Nullable
    private static CompletableFuture<CaveWorld> nextWorld;
    @Nullable
    private static String sceneId;
    @Nullable
    private static CompletableFuture<CaveMesh.Built> building;
    @Nullable
    private static CompletableFuture<CaveWorld> buildingWorld;
    @Nullable
    private static CaveMesh mesh;
    @Nullable
    private static CaveCamera camera;
    @Nullable
    private static DynamicTexture lightmap;
    @Nullable
    private static SceneLook lightmapFor;
    private static boolean dirty = true;
    private static boolean meshShaded;
    private static boolean failed;
    private static boolean introDone;
    private static boolean rememberSelection = true;
    private static double meshTime = Double.NaN;
    private static double readyTime = Double.NaN;
    private static double switchStart = Double.NaN;
    private static double waitingSince = Double.NaN;

    private CaveBackground() {
    }

    /** Starts generating the voxels in the background; models aren't needed for that. */
    public static void startGenerating() {
        if (world == null) {
            sceneId = MenuScenes.configured();
            world = load(sceneId);
        }
    }

    private static CompletableFuture<CaveWorld> load(String id) {
        return CompletableFuture.supplyAsync(() -> MenuScenes.load(id), Util.backgroundExecutor());
    }

    public static String scene() {
        return sceneId != null ? sceneId : MenuScenes.configured();
    }

    /** True once scene {@code id} is fully faded in (for the dev screenshots). */
    public static boolean isShowing(String id) {
        return isShowing(id, 1.0);
    }

    /** True once scene {@code id} has been fully faded in for {@code seconds}. */
    public static boolean isShowing(String id, double seconds) {
        return id.equals(sceneId) && nextWorld == null && !Double.isNaN(readyTime) && AnimClock.now() - readyTime > FADE_IN_SECONDS + seconds;
    }

    /**
     * Shows another scene and remembers it: the current one fades to dark, the next one fades in once it is generated,
     * meshed and (with shaders) warmed up. {@code reload} re-reads a scene that is already shown (it was re-captured).
     */
    public static void select(String id, boolean reload) {
        if (rememberSelection && GlazuliConfig.SPEC.isLoaded()) {
            GlazuliConfig.MENU_SCENE.set(id);
            GlazuliConfig.MENU_SCENE.save();
        }
        if (id.equals(sceneId) && !reload || world == null) {
            return;
        }
        sceneId = id;
        CompletableFuture<CaveWorld> next = load(id);
        if (mesh != null && isActive()) {
            nextWorld = next;
            if (Double.isNaN(switchStart)) {
                switchStart = AnimClock.now();
            }
        } else {
            swap(next);
        }
    }

    private static void swap(CompletableFuture<CaveWorld> next) {
        world = next;
        nextWorld = null;
        switchStart = Double.NaN;
        if (camera != null) {
            introDone |= camera.introFlown();
        }
        camera = null;
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
        dirty = true;
        meshTime = Double.NaN;
        readyTime = Double.NaN;
    }

    private static CaveCamera camera(CaveWorld scene) {
        if (camera == null) {
            camera = new CaveCamera(scene.rig(), scene.look().fogEnd(), introDone);
        }
        return camera;
    }

    public static ResourceManagerReloadListener reloadListener() {
        return (ResourceManager manager) -> dirty = true;
    }

    public static boolean isActive() {
        return !failed && GlazuliConfig.SPEC.isLoaded() && GlazuliConfig.CAVE_BACKGROUND.get()
                && Minecraft.getInstance().level == null;
    }

    /** Whether the title menu may play its entrance, i.e. the startup flight has (nearly) arrived. */
    public static boolean titleEntranceReady() {
        if (!isActive()) {
            return true;
        }
        if (mesh != null && camera != null) {
            return !camera.isIntroArriving();
        }
        double now = AnimClock.now();
        if (Double.isNaN(waitingSince)) {
            waitingSince = now;
        }
        return now - waitingSince > ENTRANCE_WAIT_LIMIT;
    }

    public static void onFrameStart() {
        ShaderCave.newFrame();
    }

    public static void render(GuiGraphics guiGraphics, int width, int height) {
        try {
            renderUnsafe(guiGraphics, width, height);
        } catch (RuntimeException e) {
            failed = true;
            Glazuli.LOGGER.error("Cave background failed, falling back to the vanilla panorama", e);
        }
    }

    private static void renderUnsafe(GuiGraphics guiGraphics, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        double now = AnimClock.now();
        if (nextWorld != null && now - switchStart >= SWITCH_OUT_SECONDS && nextWorld.isDone()) {
            swap(nextWorld);
        }
        pollBuild(minecraft, now);
        if (mesh == null || world == null || !world.isDone()) {
            guiGraphics.fill(0, 0, width, height, 0xFF000000 | DARK);
            return;
        }
        CaveWorld scene = world.join();
        CaveCamera cam = camera(scene);
        boolean introMayStart = (!IntroState.isPlaying() || IntroState.isOutroPlaying()) && now - readyTime > 0.15;
        cam.update(minecraft.screen, now, introMayStart);

        guiGraphics.flush();
        if (!ShaderCave.render(minecraft, scene, cam)) {
            drawScene(minecraft, mesh, cam, scene);
        }

        float fade = Double.isNaN(readyTime) ? 1.0F : 1.0F - Mth.clamp((float) ((now - readyTime) / FADE_IN_SECONDS), 0.0F, 1.0F);
        if (!Double.isNaN(switchStart)) {
            fade = Math.max(fade, Mth.clamp((float) ((now - switchStart) / SWITCH_OUT_SECONDS), 0.0F, 1.0F));
        }
        if (fade > 0.0F) {
            guiGraphics.fill(0, 0, width, height, ((int) (fade * 255.0F) << 24) | DARK);
        }
    }

    /**
     * Called by the startup overlay once loading is done, so the cave is meshed before the outro reveals it.
     * {@code render}: also render it (off-screen, under the splash); a full shader pack frame halves the splash's frame rate.
     */
    public static void prepare(boolean render) {
        if (!isActive()) {
            return;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            double now = AnimClock.now();
            pollBuild(minecraft, now);
            // Rendered under the opaque splash: the shader pack compiles and the chunks mesh before the reveal.
            if (render && mesh != null && world != null && world.isDone()) {
                CaveWorld scene = world.join();
                CaveCamera cam = camera(scene);
                cam.update(minecraft.screen, now, false);
                ShaderCave.render(minecraft, scene, cam);
            }
        } catch (RuntimeException e) {
            failed = true;
            Glazuli.LOGGER.error("Cave background failed, falling back to the vanilla panorama", e);
        }
    }

    /** True once the cave can be shown without stalling (or it won't be shown at all). */
    public static boolean isReady() {
        return !isActive() || mesh != null && world.isDone() && ShaderCave.isWarm(Minecraft.getInstance(), world.join());
    }

    /** Development screenshots switch scenes without replacing the one the player chose. */
    public static void rememberSelection(boolean remember) {
        rememberSelection = remember;
    }

    /** Development screenshots: holds the camera's idle drift still. */
    public static void freezeCameraDrift(boolean freeze) {
        CaveCamera.freezeDrift = freeze;
    }

    /** Development screenshots: looks from {@code eye} at {@code look} (scene coordinates); null eye restores the spots. */
    public static void devCamera(@Nullable org.joml.Vector3f eye, @Nullable org.joml.Vector3f look) {
        CaveCamera.devEye = eye;
        CaveCamera.devLook = look;
    }

    /** How long the startup may hold the splash for {@link #isReady()}; compiling a shader pack takes a while. */
    public static float readyWaitLimitSeconds() {
        boolean levelScene = world != null && world.isDone() && !world.isCompletedExceptionally() && world.join().needsWorld();
        return isActive() && (ShaderCave.wanted() || levelScene) ? 30.0F : 2.0F;
    }

    /** Like {@link #isReady()}, but also requires the mesh to match the current resources (after a reload). */
    public static boolean isUpToDate() {
        return !isActive() || mesh != null && !dirty && building == null;
    }

    private static void pollBuild(Minecraft minecraft, double now) {
        // During a reload the models are being swapped; wait until it's applied (our overlays linger after that).
        boolean reloading = minecraft.getOverlay() != null
                && !(minecraft.getOverlay() instanceof GlassLoadingOverlay overlay && overlay.isFinishCalled());
        // With a shader pack loaded, Iris extends the block vertex format and applies the pack's block settings to
        // whatever is tessellated, so the mesh is rebuilt when shaders are turned on or off.
        if (mesh != null && meshShaded != ShaderCave.wanted()) {
            dirty = true;
        }
        if (building == null && dirty && world != null && !reloading) {
            dirty = false;
            meshShaded = ShaderCave.wanted();
            buildingWorld = world;
            building = world.thenApplyAsync(CaveMesh::build, Util.backgroundExecutor());
        }
        if (building != null && building.isDone()) {
            CompletableFuture<CaveMesh.Built> done = building;
            building = null;
            CaveMesh.Built built = done.join();
            if (buildingWorld != world) {
                // Meshed for a scene switched away from meanwhile.
                built.close();
                return;
            }
            if (mesh != null) {
                mesh.close();
            }
            mesh = built.upload();
            if (Double.isNaN(meshTime)) {
                meshTime = now;
            }
        }
        // Revealed once the shader world (if any) is meshed too, so it doesn't pop in over the plain mesh.
        if (mesh != null && Double.isNaN(readyTime) && world.isDone()
                && (ShaderCave.isWarm(minecraft, world.join()) || now - meshTime > WARM_WAIT_LIMIT)) {
            readyTime = now;
        }
    }

    private static void drawScene(Minecraft minecraft, CaveMesh mesh, CaveCamera camera, CaveWorld scene) {
        SceneLook look = SceneClock.look(scene);
        Window window = minecraft.getWindow();
        float aspect = (float) window.getWidth() / Math.max(1, window.getHeight());
        Matrix4f projection = new Matrix4f().setPerspective(FOV, aspect, 0.05F, 256.0F);
        // The chunk shaders take fog distance from (Position + ChunkOffset), so vertices must be camera-relative.
        Matrix4f view = new Matrix4f().lookAt(0.0F, 0.0F, 0.0F, camera.target.x - camera.eye.x, camera.target.y - camera.eye.y,
                camera.target.z - camera.eye.z, 0.0F, 1.0F, 0.0F);

        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        float[] fogColor = RenderSystem.getShaderFogColor().clone();
        FogShape fogShape = RenderSystem.getShaderFogShape();
        RenderSystem.setShaderFogStart(Math.min(look.fogStart(), camera.fogEnd * 0.5F));
        RenderSystem.setShaderFogEnd(camera.fogEnd);
        RenderSystem.setShaderFogColor(look.red(), look.green(), look.blue());
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
        // The mesh doesn't cover every pixel (openings, beyond the fog end); without a colour clear those show the
        // previous screen. glClear honours the write masks: with depth writes left off (Iris leaves them so after
        // the shader cave stops), the shader world's last depth would hide parts of the mesh.
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.clearColor(look.red(), look.green(), look.blue(), 1.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        int lightmapId = lightmap(look).getId();
        for (RenderType layer : CaveMesh.LAYERS) {
            // Fabulous graphics routes translucency to a level target that doesn't exist without a world.
            if (layer == RenderType.translucent() && Minecraft.useShaderTransparency()) {
                continue;
            }            VertexBuffer buffer = mesh.buffer(layer);
            if (buffer == null) {
                continue;
            }
            layer.setupRenderState();
            // Our own copy of the chunk shader: with a shader pack loaded, Iris (and shader-patching mods) replace
            // the vanilla chunk programs with ones that write into their G-buffers instead of the screen.
            ShaderInstance own = layer == RenderType.cutout() || layer == RenderType.cutoutMipped()
                    ? GlazuliShaders.caveCutout() : GlazuliShaders.caveBlock();
            ShaderInstance shader = own != null ? own : RenderSystem.getShader();
            if (shader != null) {
                RenderSystem.setShaderTexture(2, lightmapId);
                if (shader.CHUNK_OFFSET != null) {
                    shader.CHUNK_OFFSET.set(-camera.eye.x, -camera.eye.y, -camera.eye.z);
                }
                buffer.bind();
                buffer.drawWithShader(view, projection, shader);
            }
            layer.clearRenderState();
            if (shader != null && shader.CHUNK_OFFSET != null) {
                shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
            }
        }
        VertexBuffer.unbind();

        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
        RenderSystem.setShaderFogShape(fogShape);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableDepthTest();
    }

    private static DynamicTexture lightmap(SceneLook look) {
        if (lightmap == null) {
            lightmap = new DynamicTexture(16, 16, false);
        }
        if (!look.equals(lightmapFor)) {
            lightmapFor = look;
            look.paintLightmap(lightmap.getPixels());
            lightmap.upload();
            lightmap.setFilter(true, false);
        }
        return lightmap;
    }
}
