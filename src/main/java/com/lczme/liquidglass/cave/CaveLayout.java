package com.lczme.liquidglass.cave;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Geometry shared by the generator and the camera: a winding tunnel along +Z that opens into a chamber and continues
 * past it. The camera flies the tunnel into the chamber at startup and down the far tunnel when joining a world.
 */
public final class CaveLayout {
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
}
