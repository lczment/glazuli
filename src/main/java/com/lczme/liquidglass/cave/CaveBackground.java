package com.lczme.liquidglass.cave;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.config.LiquidGlassConfig;
import com.lczme.liquidglass.intro.GlassLoadingOverlay;
import com.lczme.liquidglass.intro.IntroState;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.lczme.liquidglass.render.LiquidGlassShaders;
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
    private static final long SEED = 0x4C47_4361_7665L;
    private static final float FOV = (float) Math.toRadians(75.0);
    private static final float FADE_IN_SECONDS = 0.6F;
    /** The title menu stops waiting for the flight if the mesh isn't ready by then. */
    private static final double ENTRANCE_WAIT_LIMIT = 4.0;
    private static final int DARK = 0x050608;
    @Nullable
    private static CompletableFuture<CaveWorld> world;
    @Nullable
    private static CompletableFuture<CaveMesh.Built> building;
    @Nullable
    private static CaveMesh mesh;
    @Nullable
    private static CaveCamera camera;
    @Nullable
    private static DynamicTexture lightmap;
    private static boolean dirty = true;
    private static boolean meshShaded;
    private static boolean failed;
    private static double readyTime = Double.NaN;
    private static double waitingSince = Double.NaN;

    private CaveBackground() {
    }

    /** Starts generating the voxels in the background; models aren't needed for that. */
    public static void startGenerating() {
        if (world == null) {
            world = CompletableFuture.supplyAsync(() -> CaveGenerator.generate(SEED), Util.backgroundExecutor());
        }
    }

    public static ResourceManagerReloadListener reloadListener() {
        return (ResourceManager manager) -> dirty = true;
    }

    public static boolean isActive() {
        return !failed && LiquidGlassConfig.SPEC.isLoaded() && LiquidGlassConfig.CAVE_BACKGROUND.get()
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
            LiquidGlass.LOGGER.error("Cave background failed, falling back to the vanilla panorama", e);
        }
    }

    private static void renderUnsafe(GuiGraphics guiGraphics, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        double now = AnimClock.now();
        pollBuild(minecraft, now);
        if (mesh == null || world == null) {
            guiGraphics.fill(0, 0, width, height, 0xFF000000 | DARK);
            return;
        }
        if (camera == null) {
            camera = new CaveCamera(world.join().layout());
        }
        boolean introMayStart = (!IntroState.isPlaying() || IntroState.isOutroPlaying()) && now - readyTime > 0.15;
        camera.update(minecraft.screen, now, introMayStart);

        guiGraphics.flush();
        if (!ShaderCave.render(minecraft, world.join(), camera)) {
            drawScene(minecraft, mesh, camera);
        }

        float fade = 1.0F - Mth.clamp((float) ((now - readyTime) / FADE_IN_SECONDS), 0.0F, 1.0F);
        if (fade > 0.0F) {
            guiGraphics.fill(0, 0, width, height, ((int) (fade * 255.0F) << 24) | DARK);
        }
    }

    /** Called by the startup overlay once loading is done, so the cave is meshed before the outro reveals it. */
    public static void prepare() {
        if (!isActive()) {
            return;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            double now = AnimClock.now();
            pollBuild(minecraft, now);
            // Rendered under the opaque splash: the shader pack compiles and the chunks mesh before the reveal.
            if (mesh != null && world != null && world.isDone()) {
                if (camera == null) {
                    camera = new CaveCamera(world.join().layout());
                }
                camera.update(minecraft.screen, now, false);
                ShaderCave.render(minecraft, world.join(), camera);
            }
        } catch (RuntimeException e) {
            failed = true;
            LiquidGlass.LOGGER.error("Cave background failed, falling back to the vanilla panorama", e);
        }
    }

    /** True once the cave can be shown without stalling (or it won't be shown at all). */
    public static boolean isReady() {
        return !isActive() || mesh != null && ShaderCave.isWarm(Minecraft.getInstance());
    }

    /** How long the startup may hold the splash for {@link #isReady()}; compiling a shader pack takes a while. */
    public static float readyWaitLimitSeconds() {
        return isActive() && ShaderCave.wanted() ? 30.0F : 2.0F;
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
            meshShaded = ShaderCave.wanted();            building = world.thenApplyAsync(CaveMesh::build, Util.backgroundExecutor());
        }
        if (building != null && building.isDone()) {
            CompletableFuture<CaveMesh.Built> done = building;
            building = null;
            CaveMesh.Built built = done.join();
            if (mesh != null) {
                mesh.close();
            }
            mesh = built.upload();
            if (Double.isNaN(readyTime)) {
                readyTime = now;
            }
        }
    }

    private static void drawScene(Minecraft minecraft, CaveMesh mesh, CaveCamera camera) {
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
        RenderSystem.setShaderFogStart(12.0F);
        RenderSystem.setShaderFogEnd(camera.fogEnd);
        RenderSystem.setShaderFogColor(((DARK >> 16) & 0xFF) / 255.0F, ((DARK >> 8) & 0xFF) / 255.0F, (DARK & 0xFF) / 255.0F);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
        // The mesh doesn't cover every pixel (openings, beyond the fog end); without a colour clear those show the
        // previous screen. glClear honours the write masks: with depth writes left off (Iris leaves them so after
        // the shader cave stops), the shader world's last depth would hide parts of the mesh.
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.clearColor(((DARK >> 16) & 0xFF) / 255.0F, ((DARK >> 8) & 0xFF) / 255.0F, (DARK & 0xFF) / 255.0F, 1.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        int lightmapId = lightmap().getId();
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
                    ? LiquidGlassShaders.caveCutout() : LiquidGlassShaders.caveBlock();
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

    /** Warm torch-lit light map (no sky light underground) with a faint cool ambient so unlit rock isn't pure black. */
    private static DynamicTexture lightmap() {
        if (lightmap == null) {
            lightmap = new DynamicTexture(16, 16, false);
            NativeImage pixels = lightmap.getPixels();
            for (int block = 0; block < 16; block++) {
                float f = block / 15.0F;
                float lum = (float) Math.pow(f, 1.35) * 1.15F;
                int r = toByte(0.13F + 1.00F * lum);
                int g = toByte(0.15F + 0.82F * lum);
                int b = toByte(0.20F + 0.60F * lum);
                for (int sky = 0; sky < 16; sky++) {
                    pixels.setPixelRGBA(block, sky, 0xFF000000 | b << 16 | g << 8 | r);
                }
            }
            lightmap.upload();
            lightmap.setFilter(true, false);
        }
        return lightmap;
    }

    private static int toByte(float value) {
        return Mth.clamp((int) (value * 255.0F), 0, 255);
    }
}
