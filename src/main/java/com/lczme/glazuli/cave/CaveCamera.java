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
    /** Flights between spots cruise at this many blocks per second, so a long way takes longer (up to a limit). */
    private static final float CRUISE_SPEED = 24.0F;
    private static final float MAX_TRANSITION_SECONDS = 2.4F;
    private static final float ACCELERATION_SECONDS = 0.6F;
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
    /** Length of the flight to the current spot and how long it takes. */
    private float routeLength;
    private float transitionSeconds = TRANSITION_SECONDS;

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
        // A flight of a block or two (no room behind the title spot) has no direction worth looking along.
        float w = path.get(0).distance(path.get(path.size() - 1)) < 3.0F ? 1.0F : smoothstep(0.6F, 1.0F, e);
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
            fromEye.set(eye);
            fromTarget.set(target);
            routeLength = fromEye.distance(rig.spotEye(next));
            transitionSeconds = Mth.clamp(routeLength / CRUISE_SPEED + 0.9F, TRANSITION_SECONDS, MAX_TRANSITION_SECONDS);
            modeStart = cut ? now - transitionSeconds : now;
        }
        float s = travelled((float) (now - modeStart));
        float u = routeLength > 0.0F ? s / routeLength : 1.0F;
        Vector3f spotEye = spotEye(next, now, new Vector3f());
        Vector3f spotLook = spotLook(next, now, new Vector3f());
        eye.set(fromEye).lerp(spotEye, u);
        Vector3f fromDir = new Vector3f(fromTarget).sub(fromEye);
        float fromDistance = Math.max(fromDir.length(), 0.01F);
        fromDir.div(fromDistance);
        Vector3f toDir = new Vector3f(spotLook).sub(eye);
        float toDistance = Math.max(toDir.length(), 0.01F);
        toDir.div(toDistance);
        float w = smoothstep(0.0F, 1.0F, u);
        target.set(blend(fromDir, toDir, w)).mul(Mth.lerp(w, fromDistance, toDistance)).add(eye);
        fogEnd = baseFogEnd;
    }

    /** Normalised blend of two unit directions, turning at an even rate (falls back to {@code b} when opposite). */
    private static Vector3f blend(Vector3f a, Vector3f b, float w) {
        Vector3f out = new Vector3f(a).mul(1.0F - w).add(b.x * w, b.y * w, b.z * w);
        return out.lengthSquared() < 1.0E-6F ? new Vector3f(b) : out.normalize();
    }

    /**
     * Distance along the route after {@code t} seconds: speeds up, cruises, slows down, arriving after
     * {@link #transitionSeconds}.
     */
    private float travelled(float t) {
        float total = transitionSeconds;
        if (t >= total || routeLength <= 0.0F) {
            return routeLength;
        }
        float ramp = Math.min(ACCELERATION_SECONDS, total * 0.4F);
        float cruise = routeLength / (total - ramp);
        if (t < ramp) {
            return 0.5F * cruise / ramp * t * t;
        }
        if (t > total - ramp) {
            float left = total - t;
            return routeLength - 0.5F * cruise / ramp * left * left;
        }
        return cruise * (t - 0.5F * ramp);
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
