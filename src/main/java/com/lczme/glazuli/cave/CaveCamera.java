package com.lczme.glazuli.cave;

import com.lczme.glazuli.anim.Easing;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Where the background camera is, driven by the open screen: a one-time flight through the tunnel into the chamber
 * for the first title screen, a viewpoint in the chamber per kind of menu, and a dive down the far tunnel while a
 * world or server is loading.
 */
final class CaveCamera {
    static final float FOG_END = 60.0F;
    private static final float INTRO_SECONDS = 3.4F;
    private static final float TRANSITION_SECONDS = 1.6F;
    private static final float DIVE_TIME_CONSTANT = 2.4F;
    /** A screen change after this long without rendering the cave cuts instead of flying. */
    private static final double CUT_GAP_SECONDS = 1.0;

    enum Mode {
        INTRO,
        SPOT,
        DIVE
    }

    enum Spot {
        TITLE(0.0F, 2.2F, -15.0F, 0.0F, 1.2F, 6.0F),
        WORLDS(-9.0F, 2.0F, -6.0F, 6.0F, 1.0F, 2.0F),
        SERVERS(9.0F, 3.0F, 2.0F, -6.0F, 2.0F, -6.0F),
        OPTIONS(-2.0F, 6.5F, 9.0F, 1.0F, 0.0F, 3.0F),
        MODS(5.0F, 1.6F, 12.0F, -3.0F, 3.0F, -4.0F),
        OTHER(-6.0F, 4.5F, 8.0F, 4.0F, 2.0F, -2.0F);

        final float eyeX, eyeY, eyeZ, lookX, lookY, lookZ;

        Spot(float eyeX, float eyeY, float eyeZ, float lookX, float lookY, float lookZ) {
            this.eyeX = eyeX;
            this.eyeY = eyeY;
            this.eyeZ = eyeZ;
            this.lookX = lookX;
            this.lookY = lookY;
            this.lookZ = lookZ;
        }
    }

    private final CameraRig rig;
    private final float baseFogEnd;
    final Vector3f eye = new Vector3f();
    final Vector3f target = new Vector3f();
    float fogEnd;

    private final Vector3f fromEye = new Vector3f();
    private final Vector3f fromTarget = new Vector3f();
    @Nullable
    private Mode mode;
    @Nullable
    private Spot spot;
    private double modeStart;
    private double lastUpdate = Double.NEGATIVE_INFINITY;
    private boolean introFlown;
    private boolean introStarted;
    private float introProgress;
    private List<Vector3f> path = List.of();

    /** {@code introFlown}: a scene switched to later starts at its spot instead of replaying the startup flight. */
    CaveCamera(CameraRig rig, float fogEnd, boolean introFlown) {
        this.rig = rig;
        this.baseFogEnd = fogEnd;
        this.fogEnd = fogEnd;
        this.introFlown = introFlown;
    }

    boolean introFlown() {
        return introFlown;
    }

    /** True while the first-launch flight hasn't (nearly) arrived; the title menu waits for it. */
    boolean isIntroArriving() {
        return mode == Mode.INTRO && introProgress < 0.72F;
    }

    void update(@Nullable Screen screen, double now, boolean introMayStart) {
        boolean gap = now - lastUpdate > CUT_GAP_SECONDS;
        lastUpdate = now;
        if (devEye != null && devLook != null) {
            eye.set(devEye);
            target.set(devLook);
            fogEnd = baseFogEnd;
        } else if (isDiveScreen(screen)) {
            updateDive(now);
        } else if (devSpot != null) {
            updateSpot(devSpot, now, gap);
        } else if (!introFlown && screen instanceof TitleScreen) {
            updateIntro(now, introMayStart);
        } else {
            updateSpot(spotFor(screen), now, gap);
        }
    }

    private void updateIntro(double now, boolean mayStart) {
        if (mode != Mode.INTRO) {
            mode = Mode.INTRO;
            introStarted = false;
            path = rig.introPath();
        }
        if (!introStarted && mayStart) {
            introStarted = true;
            modeStart = now;
        }
        float u = introStarted ? Mth.clamp((float) ((now - modeStart) / INTRO_SECONDS), 0.0F, 1.0F) : 0.0F;
        introProgress = u;
        float e = Easing.IN_OUT_CUBIC.apply(u);
        catmullRom(path, e, eye);
        Vector3f lookAhead = catmullRom(path, Math.min(1.0F, e + 0.08F), new Vector3f());
        Vector3f titleLook = spotLook(Spot.TITLE, now, new Vector3f());
        float w = smoothstep(0.6F, 1.0F, e);
        if (e + 0.08F >= 1.0F) {
            lookAhead.set(titleLook);
        }
        target.set(lookAhead).lerp(titleLook, w);
        fogEnd = baseFogEnd;
        if (u >= 1.0F) {
            introFlown = true;
            updateSpot(Spot.TITLE, now, false);
        }
    }

    private void updateDive(double now) {
        if (mode != Mode.DIVE) {
            mode = Mode.DIVE;
            modeStart = now;
            path = rig.divePath(eye);
        }
        float t = (float) (now - modeStart);
        float e = (1.0F - (float) Math.exp(-t / DIVE_TIME_CONSTANT)) * smoothstep(0.0F, 0.9F, t);
        catmullRom(path, e * 0.92F, eye);
        catmullRom(path, e * 0.92F + 0.08F, target);
        fogEnd = Mth.lerp(e * e, baseFogEnd, 12.0F);
    }

    private void updateSpot(Spot next, double now, boolean gap) {
        if (mode != Mode.SPOT || spot != next) {
            boolean cut = mode == Mode.DIVE || mode == null || gap;
            mode = Mode.SPOT;
            spot = next;
            modeStart = cut ? now - TRANSITION_SECONDS : now;
            fromEye.set(eye);
            fromTarget.set(target);
        }
        float k = Easing.IN_OUT_CUBIC.apply(Mth.clamp((float) ((now - modeStart) / TRANSITION_SECONDS), 0.0F, 1.0F));
        Vector3f spotEye = spotEye(next, now, new Vector3f());
        Vector3f spotLook = spotLook(next, now, new Vector3f());
        eye.set(fromEye).lerp(spotEye, k);
        target.set(fromTarget).lerp(spotLook, k);
        fogEnd = baseFogEnd;
    }

    /** Development screenshots: holds the idle drift still so two frames differ only by what moves in the scene. */
    static boolean freezeDrift;
    /** Development screenshots: this spot instead of the screen's, null for the normal one. */
    @Nullable
    static Spot devSpot;
    /** Development screenshots: a fixed camera instead of the screen's spot, null for the normal one. */
    @Nullable
    static Vector3f devEye;
    @Nullable
    static Vector3f devLook;

    /** Spot position with a slow idle drift, so the menu background never looks frozen. */
    private Vector3f spotEye(Spot s, double now, Vector3f out) {
        float t = freezeDrift ? 0.0F : (float) now;
        return out.set(rig.spotEye(s))
                .add(0.35F * Mth.sin(t * 0.23F), 0.2F * Mth.sin(t * 0.31F + 1.0F), 0.35F * Mth.cos(t * 0.19F));
    }

    private Vector3f spotLook(Spot s, double now, Vector3f out) {
        float t = freezeDrift ? 0.0F : (float) now;
        return out.set(rig.spotLook(s))
                .add(0.5F * Mth.sin(t * 0.17F + 2.0F), 0.25F * Mth.sin(t * 0.27F), 0.5F * Mth.cos(t * 0.21F + 1.0F));
    }

    private static Spot spotFor(@Nullable Screen screen) {
        if (screen == null || screen instanceof TitleScreen || screen instanceof ScenePickerScreen) {
            return Spot.TITLE;
        }
        String name = screen.getClass().getName();
        if (name.startsWith("net.minecraft.client.gui.screens.worldselection.")) {
            return Spot.WORLDS;
        }
        if (name.startsWith("net.minecraft.client.gui.screens.multiplayer.") || name.startsWith("com.mojang.realmsclient.")
                || name.endsWith("ManageServerScreen") || name.endsWith("DirectJoinServerScreen")) {
            return Spot.SERVERS;
        }
        if (name.startsWith("net.minecraft.client.gui.screens.options.") || name.startsWith("net.minecraft.client.gui.screens.packs.")) {
            return Spot.OPTIONS;
        }
        if (name.startsWith("net.neoforged.neoforge.client.gui.")) {
            return Spot.MODS;
        }
        return Spot.OTHER;
    }

    private static boolean isDiveScreen(@Nullable Screen screen) {
        return screen instanceof ConnectScreen || screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen
                || screen instanceof GenericMessageScreen || screen instanceof ReceivingLevelScreen;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Uniform Catmull-Rom through {@code points} at {@code u} in [0, 1], end points duplicated. */
    private static Vector3f catmullRom(List<Vector3f> points, float u, Vector3f out) {
        int segments = points.size() - 1;
        if (segments < 1) {
            return out.set(points.isEmpty() ? new Vector3f() : points.get(0));
        }
        float f = Mth.clamp(u, 0.0F, 1.0F) * segments;
        int i = Math.min((int) f, segments - 1);
        float t = f - i;
        Vector3f p0 = points.get(Math.max(i - 1, 0));
        Vector3f p1 = points.get(i);
        Vector3f p2 = points.get(i + 1);
        Vector3f p3 = points.get(Math.min(i + 2, segments));
        float t2 = t * t;
        float t3 = t2 * t;
        return out.set(
                catmull(p0.x, p1.x, p2.x, p3.x, t, t2, t3),
                catmull(p0.y, p1.y, p2.y, p3.y, t, t2, t3),
                catmull(p0.z, p1.z, p2.z, p3.z, t, t2, t3));
    }

    private static float catmull(float p0, float p1, float p2, float p3, float t, float t2, float t3) {
        return 0.5F * (2.0F * p1 + (-p0 + p2) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2 + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
    }
}
