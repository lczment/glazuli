package com.lczme.glazuli.cave;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Camera for a captured scene: every menu looks out from where the player stood, turned a little per kind of menu; the
 * startup flight glides in from behind and the dive flies on ahead, as far as the terrain lets them.
 */
final class CustomRig implements CameraRig {
    private final Vector3f eye;
    private final float yaw;
    private final float pitch;
    private CaveWorld world;

    CustomRig(Vector3f eye, float yaw, float pitch) {
        this.eye = eye;
        this.yaw = yaw;
        this.pitch = Mth.clamp(pitch, -40.0F, 40.0F);
    }

    void bind(CaveWorld world) {
        this.world = world;
    }

    private static float[] turn(CaveCamera.Spot spot) {
        return switch (spot) {
            case TITLE -> new float[]{0.0F, 0.0F, 0.0F};
            case WORLDS -> new float[]{-28.0F, 3.0F, 0.0F};
            case SERVERS -> new float[]{28.0F, 3.0F, 0.0F};
            case OPTIONS -> new float[]{0.0F, -16.0F, 0.8F};
            case MODS -> new float[]{50.0F, 0.0F, 0.0F};
            case OTHER -> new float[]{-50.0F, 6.0F, 0.4F};
        };
    }

    private static Vector3f direction(float yaw, float pitch) {
        float y = yaw * Mth.DEG_TO_RAD;
        float p = pitch * Mth.DEG_TO_RAD;
        return new Vector3f(-Mth.sin(y) * Mth.cos(p), -Mth.sin(p), Mth.cos(y) * Mth.cos(p));
    }

    @Override
    public Vector3f spotEye(CaveCamera.Spot spot) {
        return new Vector3f(eye).add(0.0F, turn(spot)[2], 0.0F);
    }

    @Override
    public Vector3f spotLook(CaveCamera.Spot spot) {
        float[] t = turn(spot);
        return spotEye(spot).add(direction(yaw + t[0], pitch + t[1]).mul(8.0F));
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
