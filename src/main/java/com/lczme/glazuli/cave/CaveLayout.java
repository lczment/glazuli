package com.lczme.glazuli.cave;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Geometry shared by the generator and the camera: a winding tunnel along +Z that opens into a chamber and continues
 * past it. The camera flies the tunnel into the chamber at startup and down the far tunnel when joining a world.
 */
public final class CaveLayout implements CameraRig {
    public static final float CHAMBER_Z = 112.0F;
    public static final float CHAMBER_RX = 17.0F;
    public static final float CHAMBER_RY = 10.0F;
    public static final float CHAMBER_RZ = 20.0F;
    public static final float TUNNEL_START_Z = 5.0F;
    public static final float TUNNEL_END_Z = CaveWorld.SIZE_Z - 8.0F;

    final float chamberX = centerX(CHAMBER_Z);
    final float chamberY = centerY(CHAMBER_Z) + 2.0F;
    /** Top of the chamber's flat floor. */
    final int floorY = Mth.floor(chamberY) - 6;

    static float centerX(float z) {
        return CaveWorld.SIZE_X * 0.5F + 7.0F * Mth.sin(z * 0.035F) + 3.0F * Mth.sin(z * 0.09F + 1.3F);
    }

    static float centerY(float z) {
        return 17.0F + 3.0F * Mth.sin(z * 0.05F + 0.7F);
    }

    static float tunnelRadius(float z) {
        return 4.6F + 1.2F * Mth.sin(z * 0.07F + 2.1F);
    }

    /** Point on the tunnel's centre line, at camera height. */
    public Vector3f tunnelPoint(float z) {
        return new Vector3f(centerX(z), centerY(z), z);
    }

    /** Chamber-relative point: x/z offsets from the chamber centre, y above the floor. */
    public Vector3f chamber(float dx, float dyAboveFloor, float dz) {
        return new Vector3f(chamberX + dx, floorY + dyAboveFloor, CHAMBER_Z + dz);
    }

    @Override
    public Vector3f spotEye(CaveCamera.Spot s) {
        return chamber(s.eyeX, s.eyeY, s.eyeZ);
    }

    @Override
    public Vector3f spotLook(CaveCamera.Spot s) {
        return chamber(s.lookX, s.lookY, s.lookZ);
    }

    @Override
    public List<Vector3f> introPath() {
        List<Vector3f> points = new ArrayList<>();
        for (float z = TUNNEL_START_Z; z < CHAMBER_Z - CHAMBER_RZ; z += 12.0F) {
            points.add(tunnelPoint(z));
        }
        points.add(spotEye(CaveCamera.Spot.TITLE));
        return points;
    }

    @Override
    public List<Vector3f> divePath(Vector3f start) {
        List<Vector3f> points = new ArrayList<>();
        points.add(new Vector3f(start));
        points.add(chamber(0.0F, 2.5F, 0.0F));
        for (float z = CHAMBER_Z + CHAMBER_RZ + 2.0F; z <= TUNNEL_END_Z; z += 12.0F) {
            points.add(tunnelPoint(z));
        }
        return points;
    }
}
