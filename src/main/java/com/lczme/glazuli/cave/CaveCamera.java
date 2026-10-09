package com.lczme.glazuli.cave;

import java.util.ArrayList;
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
    /** The startup flight eases in and out over these parts of its time and cruises at a steady speed in between. */
    private static final float INTRO_EASE_IN = 0.25F;
    private static final float INTRO_EASE_OUT = 0.5F;
    private static final float INTRO_LOOK_AHEAD = 5.0F;
    /** Over its last this many blocks the startup flight turns from looking along its way to the title view. */
    private static final float INTRO_TURN_BLOCKS = 14.0F;
    /** A flight with a first look holds it over this part of the way before tilting onto the title view. */
    private static final float INTRO_LOOK_HOLD = 0.1F;
    /**
     * Flights between spots: a lead point travels a smooth path round any walls on an eased schedule (longer flights
     * take a little longer), and the eye follows it on a short critically damped spring. A change of menu mid-flight
     * starts the new path from where the camera is, along its heading, at the speed it already has.
     */
    private static final float FLIGHT_BASE_SECONDS = 0.55F;
    private static final float FLIGHT_BLOCKS_PER_SECOND = 30.0F;
    private static final float MIN_FLIGHT_SECONDS = 0.65F;
    private static final float MAX_FLIGHT_SECONDS = 1.2F;
    /** Flights speed up and slow down over this part of their time and cruise in between, keeping the top speed low. */
    private static final float FLIGHT_EASE = 0.35F;
    private static final float FOLLOW_SECONDS = 0.05F;
    /** A camera redirected mid-flight first carries on this long along its old heading, curving onto the new path. */
    private static final float CARRY_SECONDS = 0.12F;
    private static final float TURN_SMOOTH_SECONDS = 0.17F;
    private static final float DIVE_TIME_CONSTANT = 0.85F;
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
    @Nullable
    private final CaveWorld world;
    private final float baseFogEnd;
    final Vector3f eye = new Vector3f();
    final Vector3f target = new Vector3f();
    float fogEnd;

    private final Vector3f eyeVelocity = new Vector3f();
    private final Vector3f lead = new Vector3f();
    /** The flight to the current spot (its last point kept on the drifting spot), how far along and how fast. */
    private List<Vector3f> flight = List.of();
    private float[] flightPace = new float[0];
    private double flightStart;
    private float flightSeconds = 1.0F;
    private float startSpeed;
    /** Look direction as yaw/pitch in degrees (unwrapped), each with a lead angle and velocities. */
    private final float[] yaw = new float[4];
    private final float[] pitch = new float[4];
    private float lookDistance = 8.0F;
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
    private float dt;

    /** {@code introFlown}: a scene switched to later starts at its spot instead of replaying the startup flight. */
    CaveCamera(CameraRig rig, @Nullable CaveWorld world, float fogEnd, boolean introFlown) {
        this.rig = rig;
        this.world = world;
        this.baseFogEnd = fogEnd;
        this.fogEnd = fogEnd;
        this.introFlown = introFlown;
    }

    boolean introFlown() {
        return introFlown;
    }

    /** Development screenshots: flies the startup flight again. */
    void replayIntro() {
        introFlown = false;
        mode = Mode.SPOT;
    }

    /** True while the first-launch flight hasn't (nearly) arrived; the title menu waits for it. */
    boolean isIntroArriving() {
        return mode == Mode.INTRO && introProgress < 0.72F;
    }

    void update(@Nullable Screen screen, double now, boolean introMayStart) {
        boolean gap = now - lastUpdate > CUT_GAP_SECONDS;
        dt = (float) Mth.clamp(now - lastUpdate, 0.0, 0.1);
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
            Spot next = spotFor(screen);
            if (next == null) {
                next = spot == null || spot == Spot.TITLE || mode != Mode.SPOT ? Spot.OTHER : spot;
            }
            updateSpot(next, now, gap);
        }
    }

    private void updateIntro(double now, boolean mayStart) {
        if (mode != Mode.INTRO) {
            mode = Mode.INTRO;
            introStarted = false;
            path = sampled(rig.introPath());
        }
        if (!introStarted && mayStart) {
            introStarted = true;
            modeStart = now;
        }
        float u = introStarted ? Mth.clamp((float) ((now - modeStart) / rig.introSeconds()), 0.0F, 1.0F) : 0.0F;
        introProgress = u;
        float length = CameraPath.length(path);
        float s = cruise(u, INTRO_EASE_IN, INTRO_EASE_OUT) * length;
        float along = length > 0.0F ? s / length : 1.0F;
        // The way ends on the drifting title spot, so the spot's own motion takes over without a jump.
        Vector3f drift = spotEye(Spot.TITLE, now, new Vector3f()).sub(rig.spotEye(Spot.TITLE));
        CameraPath.at(path, s, eye).add(drift.mul(along));
        Vector3f titleLook = spotLook(Spot.TITLE, now, new Vector3f());
        Vector3f firstLook = rig.introLook();
        if (firstLook != null) {
            target.set(firstLook).lerp(titleLook, smoothstep(INTRO_LOOK_HOLD, 1.0F, along));
        } else if (length >= 3.0F) {
            Vector3f titleDir = new Vector3f(titleLook).sub(eye);
            float reach = Math.min(INTRO_LOOK_AHEAD, length);
            float from = Math.min(s, length - reach);
            Vector3f heading = CameraPath.at(path, from + reach, new Vector3f()).sub(CameraPath.at(path, from, new Vector3f()));
            float headingYaw = yawOf(heading);
            float w = smoothstep(Math.max(0.6F * length, length - INTRO_TURN_BLOCKS), length, s);
            float lookYaw = headingYaw + Mth.wrapDegrees(yawOf(titleDir) - headingYaw) * w;
            float lookPitch = Mth.lerp(w, pitchOf(heading), pitchOf(titleDir));
            float yawRad = lookYaw * Mth.DEG_TO_RAD;
            float pitchRad = lookPitch * Mth.DEG_TO_RAD;
            float horizontal = Mth.cos(pitchRad);
            target.set(Mth.sin(yawRad) * horizontal, Mth.sin(pitchRad), Mth.cos(yawRad) * horizontal)
                    .mul(Math.max(titleDir.length(), 1.0F)).add(eye);
        } else {
            // A flight of a block or two (no room behind the title spot) has no direction worth looking along.
            target.set(titleLook);
        }
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
        Vector3f goalEye = spotEye(next, now, new Vector3f());
        Vector3f goalDir = spotLook(next, now, new Vector3f()).sub(goalEye);
        float goalDistance = Math.max(goalDir.length(), 0.01F);
        float goalYaw = yawOf(goalDir);
        float goalPitch = pitchOf(goalDir);
        if (mode != Mode.SPOT || spot != next) {
            boolean cut = mode == Mode.DIVE || mode == null || gap;
            if (cut) {
                eye.set(goalEye);
                start(goalYaw, goalPitch);
                lookDistance = goalDistance;
            } else if (mode != Mode.SPOT) {
                Vector3f dir = new Vector3f(target).sub(eye);
                start(yawOf(dir), pitchOf(dir));
                lookDistance = Math.max(dir.length(), 0.01F);
            }
            if (cut || mode != Mode.SPOT) {
                eyeVelocity.zero();
            }
            Vector3f via = null;
            if (!cut && world != null && eyeVelocity.length() > 1.0F) {
                for (float ahead = CARRY_SECONDS; ahead > 0.05F && via == null; ahead *= 0.5F) {
                    Vector3f p = new Vector3f(eyeVelocity).mul(ahead).add(eye);
                    if (CameraPath.clear(world, eye, p)) {
                        via = p;
                    }
                }
            }
            // Routed to where the spot rests, not where its drift has it now: drifted just behind a corner, the spot
            // would send the route round past it and back. The end then follows the drift (below).
            flight = new ArrayList<>(cut || world == null ? List.of(new Vector3f(goalEye), new Vector3f(goalEye))
                    : CameraPath.find(world, eye, via, new Vector3f(rig.spotEye(next))));
            flight.set(0, new Vector3f(eye));
            flightPace = CameraPath.pace(flight);
            Vector3f heading = new Vector3f(flight.get(1)).sub(eye);
            startSpeed = via != null ? eyeVelocity.length()
                    : heading.lengthSquared() > 1.0E-6F ? Math.max(0.0F, eyeVelocity.dot(heading.normalize())) : 0.0F;
            float length = CameraPath.length(flight);
            flightSeconds = Mth.clamp(FLIGHT_BASE_SECONDS + length / FLIGHT_BLOCKS_PER_SECOND, MIN_FLIGHT_SECONDS, MAX_FLIGHT_SECONDS);
            if (startSpeed * flightSeconds > 2.0F * length) {
                flightSeconds = Math.max(0.4F, 2.0F * length / startSpeed);
            }
            flightStart = now;
            mode = Mode.SPOT;
            spot = next;
        }
        flight.get(flight.size() - 1).set(goalEye);
        float paced = CameraPath.pacedLength(flight, flightPace);
        float t = Mth.clamp((float) (now - flightStart) / flightSeconds, 0.0F, 1.0F);
        float carry = paced > 1.0E-3F ? startSpeed * flightSeconds * flightPace[0] / paced : 0.0F;
        // Starts at the speed the camera already has, ends at rest, no jolt in acceleration at either end.
        float eased = cruise(t, FLIGHT_EASE, FLIGHT_EASE) + carry * (t - 6.0F * t * t * t + 8.0F * t * t * t * t - 3.0F * t * t * t * t * t);
        CameraPath.atPaced(flight, flightPace, eased * paced, lead);
        smoothDamp(eye, lead, eyeVelocity, FOLLOW_SECONDS, dt);
        turn(yaw, yaw[2] + Mth.wrapDegrees(goalYaw - yaw[2]));
        turn(pitch, goalPitch);
        lookDistance = Mth.lerp(1.0F - (float) Math.exp(-dt * 10.0F), lookDistance, goalDistance);
        float yawRad = yaw[0] * Mth.DEG_TO_RAD;
        float pitchRad = pitch[0] * Mth.DEG_TO_RAD;
        float horizontal = Mth.cos(pitchRad);
        target.set(Mth.sin(yawRad) * horizontal, Mth.sin(pitchRad), Mth.cos(yawRad) * horizontal).mul(lookDistance).add(eye);
        fogEnd = baseFogEnd;
    }

    /** Angle state: {@code [value, velocity, lead, lead velocity]}. */
    private void start(float yawValue, float pitchValue) {
        yaw[0] = yaw[2] = yawValue;
        pitch[0] = pitch[2] = pitchValue;
        yaw[1] = yaw[3] = pitch[1] = pitch[3] = 0.0F;
    }

    private void turn(float[] angle, float goal) {
        float[] v = {angle[3]};
        angle[2] = smoothDamp(angle[2], goal, v, TURN_SMOOTH_SECONDS, dt);
        angle[3] = v[0];
        v[0] = angle[1];
        angle[0] = smoothDamp(angle[0], angle[2], v, TURN_SMOOTH_SECONDS, dt);
        angle[1] = v[0];
    }

    private static float yawOf(Vector3f dir) {
        return (float) Math.toDegrees(Math.atan2(dir.x, dir.z));
    }

    private static float pitchOf(Vector3f dir) {
        return (float) Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
    }

    /** Critically damped approach of {@code value} to {@code goal} (Game Programming Gems 4, 1.10). */
    private static float smoothDamp(float value, float goal, float[] velocity, float smoothTime, float dt) {
        float omega = 2.0F / smoothTime;
        float x = omega * dt;
        float decay = 1.0F / (1.0F + x + 0.48F * x * x + 0.235F * x * x * x);
        float change = value - goal;
        float temp = (velocity[0] + omega * change) * dt;
        velocity[0] = (velocity[0] - omega * temp) * decay;
        return goal + (change + temp) * decay;
    }

    private static void smoothDamp(Vector3f value, Vector3f goal, Vector3f velocity, float smoothTime, float dt) {
        float omega = 2.0F / smoothTime;
        float x = omega * dt;
        float decay = 1.0F / (1.0F + x + 0.48F * x * x + 0.235F * x * x * x);
        Vector3f change = new Vector3f(value).sub(goal);
        Vector3f temp = new Vector3f(change).mul(omega).add(velocity).mul(dt);
        velocity.sub(temp.x * omega, temp.y * omega, temp.z * omega).mul(decay);
        value.set(change).add(temp).mul(decay).add(goal);
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

    /** The spot for a kind of menu, or null for a screen of no known kind (a dialog keeps the spot it opened over). */
    @Nullable
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
        if (name.startsWith("net.minecraft.client.gui.screens.options.") || name.startsWith("net.minecraft.client.gui.screens.packs.")
                || name.contains("sodium") || name.startsWith("net.irisshaders.")) {
            return Spot.OPTIONS;
        }
        if (name.startsWith("net.neoforged.neoforge.client.gui.")) {
            return Spot.MODS;
        }
        return null;
    }

    private static boolean isDiveScreen(@Nullable Screen screen) {
        return screen instanceof ConnectScreen || screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen
                || screen instanceof GenericMessageScreen || screen instanceof ReceivingLevelScreen;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Fraction of the way travelled at time fraction {@code u}: speed rises smoothly from rest over the first {@code a}
     * of the time, holds, and falls smoothly to rest over the last {@code b}.
     */
    private static float cruise(float u, float a, float b) {
        float total = 1.0F - 0.5F * a - 0.5F * b;
        float d;
        if (u < a) {
            d = a * rampIntegral(u / a);
        } else if (u > 1.0F - b) {
            d = total - b * rampIntegral((1.0F - u) / b);
        } else {
            d = 0.5F * a + (u - a);
        }
        return Mth.clamp(d / total, 0.0F, 1.0F);
    }

    /** Integral of smoothstep from 0 to {@code x}. */
    private static float rampIntegral(float x) {
        return x * x * x * (1.0F - 0.5F * x);
    }

    /** Catmull-Rom through {@code points} as a dense polyline, to travel along by distance. */
    private static List<Vector3f> sampled(List<Vector3f> points) {
        if (points.size() < 2) {
            return points.isEmpty() ? List.of(new Vector3f()) : points;
        }
        int segments = points.size() - 1;
        int samples = segments * 24;
        List<Vector3f> out = new ArrayList<>(samples + 1);
        for (int i = 0; i <= samples; i++) {
            out.add(catmullRom(points, (float) i / samples, new Vector3f()));
        }
        return out;
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
