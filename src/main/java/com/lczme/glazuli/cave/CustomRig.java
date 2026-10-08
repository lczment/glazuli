package com.lczme.glazuli.cave;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Camera for a scene: either a shot per menu planned for it, or (captured scenes) the title looking out from where the
 * player stood and every other menu swinging the camera a few blocks around the point it was looking at; the
 * startup flight glides in from behind and the dive flies on ahead, as far as the terrain lets them.
 */
final class CustomRig implements CameraRig {
    private final Vector3f eye;
    private final float yaw;
    private final float pitch;
    private CaveWorld world;
    private final Vector3f[] spotEyes;
    private final Vector3f[] spotLooks;
    private final boolean planned;
    private List<Vector3f> introWaypoints = List.of();
    private float introSeconds = 3.4F;

    CustomRig(Vector3f eye, float yaw, float pitch) {
        this.eye = eye;
        this.yaw = yaw;
        this.pitch = Mth.clamp(pitch, -40.0F, 40.0F);
        this.spotEyes = new Vector3f[CaveCamera.Spot.values().length];
        this.spotLooks = new Vector3f[CaveCamera.Spot.values().length];
        this.planned = false;
        placeSpots();
    }

    /** A camera per menu chosen for the scene, indexed by {@link CaveCamera.Spot#ordinal()}. */
    CustomRig(Vector3f[] eyes, Vector3f[] looks) {
        Vector3f title = eyes[CaveCamera.Spot.TITLE.ordinal()];
        Vector3f d = new Vector3f(looks[CaveCamera.Spot.TITLE.ordinal()]).sub(title);
        this.eye = title;
        this.yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        this.pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))), -40.0F, 40.0F);
        this.spotEyes = eyes;
        this.spotLooks = looks;
        this.planned = true;
    }

    /** A startup flight planned for the scene: through {@code waypoints} (evenly spaced) on to the title spot. */
    CustomRig intro(float seconds, Vector3f... waypoints) {
        this.introSeconds = seconds;
        this.introWaypoints = List.of(waypoints);
        return this;
    }

    @Override
    public float introSeconds() {
        return introSeconds;
    }

    void bind(CaveWorld world) {
        this.world = world;
        if (!planned) {
            placeSpots();
        }
    }

    /**
     * Per menu: eye offset from the captured eye and look offset from the focus point, both as (right, up, forward).
     * Mirrors the cave chamber's spots, where every menu moves the camera several blocks around a shared centre.
     */
    private static float[] offsets(CaveCamera.Spot spot) {
        return switch (spot) {
            case TITLE -> new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F};
            case WORLDS -> new float[]{-8.0F, 0.3F, 4.0F, 5.0F, -0.5F, 2.0F};
            case SERVERS -> new float[]{8.0F, 1.0F, 5.0F, -5.0F, 0.5F, 3.0F};
            case OPTIONS -> new float[]{-2.0F, 4.0F, 7.0F, 1.0F, -2.0F, 4.0F};
            case MODS -> new float[]{5.0F, -0.3F, 9.0F, -6.0F, 1.0F, 6.0F};
            case OTHER -> new float[]{-6.0F, 2.5F, 7.0F, 6.0F, 0.5F, 4.0F};
        };
    }

    private void placeSpots() {
        Vector3f view = direction(yaw, pitch);
        float focusDist = Mth.clamp(reach(eye, view, 24.0F) - 2.0F, 4.0F, 22.0F);
        Vector3f focus = new Vector3f(view).mul(focusDist).add(eye);
        float y = yaw * Mth.DEG_TO_RAD;
        Vector3f right = new Vector3f(-Mth.cos(y), 0.0F, -Mth.sin(y));
        Vector3f forward = new Vector3f(-Mth.sin(y), 0.0F, Mth.cos(y));
        float scale = focusDist / 14.0F;
        for (CaveCamera.Spot spot : CaveCamera.Spot.values()) {
            float[] o = offsets(spot);
            Vector3f move = new Vector3f(right).mul(o[0]).add(0.0F, o[1], 0.0F)
                .add(new Vector3f(forward).mul(Math.min(o[2], focusDist * 0.6F)));
            float len = move.length();
            if (len > 0.0F) {
                float d = Math.max(0.0F, reach(eye, new Vector3f(move).div(len), len + 1.0F) - 1.0F);
                move.mul(Math.min(len, d) / len);
            }
            spotEyes[spot.ordinal()] = move.add(eye);
            spotLooks[spot.ordinal()] = new Vector3f(right).mul(o[3] * scale).add(0.0F, o[4] * scale, 0.0F)
                .add(new Vector3f(forward).mul(o[5] * scale)).add(focus);
        }
    }

    private static Vector3f direction(float yaw, float pitch) {
        float y = yaw * Mth.DEG_TO_RAD;
        float p = pitch * Mth.DEG_TO_RAD;
        return new Vector3f(-Mth.sin(y) * Mth.cos(p), -Mth.sin(p), Mth.cos(y) * Mth.cos(p));
    }

    @Override
    public Vector3f spotEye(CaveCamera.Spot spot) {
        return new Vector3f(spotEyes[spot.ordinal()]);
    }

    @Override
    public Vector3f spotLook(CaveCamera.Spot spot) {
        return new Vector3f(spotLooks[spot.ordinal()]);
    }

    private boolean free(Vector3f p) {
        return world == null || !CaveWorld.isOpaqueCube(world.get(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z)));
    }

    /** How far from {@code from} along {@code step} (per block) the camera can travel, up to {@code max}. */
    private float reach(Vector3f from, Vector3f step, float max) {
        float d = 0.0F;
        Vector3f p = new Vector3f();
        while (d + 0.5F <= max && free(from.add(step.x * (d + 0.5F), step.y * (d + 0.5F), step.z * (d + 0.5F), p))) {
            d += 0.5F;
        }
        return d;
    }

    @Override
    public List<Vector3f> introPath() {
        if (!introWaypoints.isEmpty()) {
            List<Vector3f> points = new ArrayList<>();
            introWaypoints.forEach(p -> points.add(new Vector3f(p)));
            points.add(spotEye(CaveCamera.Spot.TITLE));
            return points;
        }
        Vector3f back = direction(yaw + 180.0F, 0.0F).add(0.0F, 0.3F, 0.0F);
        float d = Math.max(0.0F, reach(eye, back, 16.0F) - 1.0F);
        List<Vector3f> points = new ArrayList<>();
        if (d < 2.0F) {
            points.add(new Vector3f(eye).add(0.0F, 0.8F, 0.0F));
        } else {
            points.add(new Vector3f(back).mul(d).add(eye));
            points.add(new Vector3f(back).mul(d * 0.45F).add(eye));
        }
        points.add(spotEye(CaveCamera.Spot.TITLE));
        return points;
    }

    @Override
    public List<Vector3f> divePath(Vector3f start) {
        Vector3f ahead = direction(yaw, Math.max(pitch, 0.0F) * 0.5F);
        float d = Math.max(1.0F, reach(eye, ahead, 40.0F) - 1.0F);
        List<Vector3f> points = new ArrayList<>();
        points.add(new Vector3f(start));
        for (int i = 1; i <= 3; i++) {
            points.add(new Vector3f(ahead).mul(d * i / 3.0F).add(eye));
        }
        return points;
    }
}
