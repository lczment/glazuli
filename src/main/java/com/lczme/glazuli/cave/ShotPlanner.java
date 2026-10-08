package com.lczme.glazuli.cave;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.joml.Vector3f;

/**
 * Chooses a camera per menu for a built scene. The title keeps its establishing shot; every other menu frames a
 * different part of the structure, from the candidate eye around it that sees the most structure blocks, isn't a
 * near-repeat of a shot already taken and can be flown to from the title camera without passing through anything.
 */
final class ShotPlanner {
    private static final int RAYS_X = 7;
    private static final int RAYS_Y = 4;
    private static final float TAN_X = (float) Math.tan(Math.toRadians(36.0));
    private static final float TAN_Y = (float) Math.tan(Math.toRadians(21.0));
    private static final float MAX_RAY = 56.0F;

    private final CaveWorld world;
    /** Per scene cell: 1 + the index of the structure piece its block belongs to, 0 for terrain and air. */
    private final short[] pieceOf;
    private final boolean[] seen;

    private ShotPlanner(CaveWorld world, StructureBuilder builder, BoundingBox area) {
        this.world = world;
        this.pieceOf = new short[world.blocks.length];
        int count = Math.min(builder.pieces.size(), Short.MAX_VALUE - 1);
        this.seen = new boolean[count + 1];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int p = 0; p < count; p++) {
            BoundingBox box = builder.pieces.get(p).getBoundingBox();
            for (int z = Math.max(box.minZ(), area.minZ()); z <= Math.min(box.maxZ(), area.maxZ()); z++) {
                for (int y = Math.max(box.minY(), area.minY()); y <= Math.min(box.maxY(), area.maxY()); y++) {
                    for (int x = Math.max(box.minX(), area.minX()); x <= Math.min(box.maxX(), area.maxX()); x++) {
                        int i = world.index(x - area.minX(), y - area.minY(), z - area.minZ());
                        BlockState state = pieceOf[i] == 0 ? builder.blocks.get(pos.set(x, y, z)) : null;
                        if (state != null && !state.isAir()) {
                            pieceOf[i] = (short) (p + 1);
                        }
                    }
                }
            }
        }
    }

    /**
     * Centroids (scene coordinates) of the structure pieces with at least {@code minBlocks} blocks above their floor,
     * biggest first, at most {@code max} of them and none within {@code spacing} of a bigger one.
     */
    static List<Vector3f> subjects(StructureBuilder builder, BoundingBox area, int minBlocks, float spacing, int max) {
        record Piece(Vector3f centre, int blocks) {
        }
        List<Piece> pieces = new ArrayList<>();
        for (StructurePiece piece : builder.pieces) {
            BoundingBox box = piece.getBoundingBox();
            int count = 0;
            double sx = 0;
            double sy = 0;
            double sz = 0;
            double sxx = 0;
            double szz = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = box.minY() + 2; y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        BlockState state = builder.blocks.get(pos.set(x, y, z));
                        if (state != null && !state.isAir()) {
                            count++;
                            sx += x;
                            sy += y;
                            sz += z;
                            sxx += (double) x * x;
                            szz += (double) z * z;
                        }
                    }
                }
            }
            if (count < minBlocks) {
                continue;
            }
            double spread = sxx / count - (sx / count) * (sx / count) + szz / count - (sz / count) * (sz / count);
            if (spread < 2.0 * 2.0) {
                // A pillar or lamp post: no subject for a whole shot.
                continue;
            }
            Vector3f centre = new Vector3f((float) (sx / count) + 0.5F, (float) (sy / count) + 0.5F, (float) (sz / count) + 0.5F);
            if (area.isInside(Mth.floor(centre.x), Mth.floor(centre.y), Mth.floor(centre.z))) {
                pieces.add(new Piece(centre.sub(area.minX(), area.minY(), area.minZ()), count));
            }
        }
        pieces.sort(Comparator.comparingInt(Piece::blocks).reversed());
        List<Vector3f> subjects = new ArrayList<>();
        for (Piece piece : pieces) {
            if (subjects.size() < max && subjects.stream().allMatch(s -> s.distance(piece.centre()) >= spacing)) {
                subjects.add(piece.centre());
            }
        }
        return subjects;
    }

    /**
     * Sets the scene's camera: the title at {@code titleEye} looking at {@code titleLook}, the other menus around
     * {@code subjects}, each from {@code distances} away and {@code heights} above it.
     */
    static void plan(CaveWorld world, StructureBuilder builder, BoundingBox area, List<Vector3f> subjects,
                     Vector3f titleEye, Vector3f titleLook, float[] distances, float[] heights) {
        new ShotPlanner(world, builder, area).place(subjects, titleEye, titleLook, distances, heights);
    }

    private record Shot(Vector3f eye, Vector3f look, int subject, float score) {
    }

    private void place(List<Vector3f> subjects, Vector3f titleEye, Vector3f titleLook, float[] distances, float[] heights) {
        List<Shot> clear = new ArrayList<>();
        List<Shot> blocked = new ArrayList<>();
        for (int i = 0; i < subjects.size(); i++) {
            Vector3f subject = subjects.get(i);
            for (int yaw = 0; yaw < 360; yaw += 30) {
                float rad = yaw * Mth.DEG_TO_RAD;
                for (float distance : distances) {
                    for (float height : heights) {
                        Vector3f eye = new Vector3f(subject.x + Mth.sin(rad) * distance, subject.y + height,
                                subject.z - Mth.cos(rad) * distance);
                        if (!BuiltScenes.roomy(world, eye) || firstHit(eye, subject) < 0.6F * eye.distance(subject)) {
                            continue;
                        }
                        Shot shot = new Shot(eye, subject, i, view(eye, subject));
                        (clearPath(titleEye, eye) ? clear : blocked).add(shot);
                    }
                }
            }
        }
        Comparator<Shot> best = Comparator.comparingDouble(Shot::score).reversed();
        clear.sort(best);
        blocked.sort(best);
        List<Shot> chosen = new ArrayList<>();
        chosen.add(new Shot(titleEye, titleLook, -1, 0.0F));
        int spots = CaveCamera.Spot.values().length;
        for (List<Shot> pool : List.of(clear, blocked)) {
            for (boolean reuse : new boolean[]{false, true}) {
                for (Shot shot : pool) {
                    if (chosen.size() < spots && !repeats(shot, chosen, reuse)) {
                        chosen.add(shot);
                    }
                }
            }
        }
        while (chosen.size() < spots) {
            chosen.add(chosen.get(0));
        }
        Vector3f[] eyes = new Vector3f[spots];
        Vector3f[] looks = new Vector3f[spots];
        for (int i = 0; i < spots; i++) {
            eyes[i] = chosen.get(i).eye();
            looks[i] = chosen.get(i).look();
        }
        CustomRig rig = new CustomRig(eyes, looks);
        rig.bind(world);
        world.rig = rig;
    }

    /** Too close to a shot already chosen: the same subject (unless {@code reuse}), eye or a similar view. */
    private static boolean repeats(Shot shot, List<Shot> chosen, boolean reuse) {
        Vector3f dir = new Vector3f(shot.look()).sub(shot.eye()).normalize();
        for (Shot other : chosen) {
            if (!reuse && other.subject() == shot.subject()) {
                return true;
            }
            Vector3f otherDir = new Vector3f(other.look()).sub(other.eye()).normalize();
            if (shot.eye().distance(other.eye()) < 6.0F
                    || shot.look().distance(other.look()) < 10.0F && dir.dot(otherDir) > 0.7F) {
                return true;
            }
        }
        return false;
    }

    /**
     * How much of the view from {@code eye} towards {@code look} is structure (rays across the frame, centre doubled),
     * more for every further piece in it: a street of houses beats one wall filling the frame.
     */
    private float view(Vector3f eye, Vector3f look) {
        java.util.Arrays.fill(seen, false);
        Vector3f forward = new Vector3f(look).sub(eye).normalize();
        Vector3f right = new Vector3f(forward).cross(0.0F, 1.0F, 0.0F);
        if (right.lengthSquared() < 1.0E-4F) {
            right.set(1.0F, 0.0F, 0.0F);
        }
        right.normalize();
        Vector3f up = new Vector3f(right).cross(forward).normalize();
        Vector3f dir = new Vector3f();
        float score = 2.0F * ray(eye, forward);
        for (int i = 0; i < RAYS_X; i++) {
            float ax = TAN_X * ((i + 0.5F) / RAYS_X * 2.0F - 1.0F);
            for (int j = 0; j < RAYS_Y; j++) {
                float ay = TAN_Y * ((j + 0.5F) / RAYS_Y * 2.0F - 1.0F);
                dir.set(forward).add(right.x * ax + up.x * ay, right.y * ax + up.y * ay, right.z * ax + up.z * ay).normalize();
                score += ray(eye, dir);
            }
        }
        int pieces = 0;
        for (boolean s : seen) {
            pieces += s ? 1 : 0;
        }
        return score / (RAYS_X * RAYS_Y + 2) * (1.0F + 0.3F * Math.min(Math.max(pieces - 1, 0), 4));
    }

    /**
     * Up to 1 for a ray ending on structure (less when it is close, so one wall doesn't fill the frame), a little for
     * terrain, nothing for sky, negative for anything right in front of the lens.
     */
    private float ray(Vector3f eye, Vector3f dir) {
        for (float d = 0.5F; d < MAX_RAY; d += 0.75F) {
            int x = Mth.floor(eye.x + dir.x * d);
            int y = Mth.floor(eye.y + dir.y * d);
            int z = Mth.floor(eye.z + dir.z * d);
            if (!world.contains(x, y, z)) {
                return 0.0F;
            }
            int i = world.index(x, y, z);
            BlockState state = world.blocks[i];
            if (!state.isAir() && !state.canBeReplaced()) {
                if (d < 2.5F) {
                    return -1.5F;
                }
                if (d < 5.0F) {
                    return -0.4F;
                }
                int piece = pieceOf[i];
                if (piece == 0) {
                    return 0.12F;
                }
                seen[piece] = true;
                return 0.3F + 0.7F * Mth.clamp((d - 5.0F) / 7.0F, 0.0F, 1.0F);
            }
        }
        return 0.0F;
    }

    /** How far the line from {@code eye} towards {@code target} gets before something visible is in the way. */
    private float firstHit(Vector3f eye, Vector3f target) {
        Vector3f dir = new Vector3f(target).sub(eye).normalize();
        for (float d = 0.5F; d < MAX_RAY; d += 0.5F) {
            int x = Mth.floor(eye.x + dir.x * d);
            int y = Mth.floor(eye.y + dir.y * d);
            int z = Mth.floor(eye.z + dir.z * d);
            if (!world.contains(x, y, z)) {
                return MAX_RAY;
            }
            BlockState state = world.blocks[world.index(x, y, z)];
            if (!state.isAir() && !state.canBeReplaced()) {
                return d;
            }
        }
        return MAX_RAY;
    }

    private boolean clearPath(Vector3f from, Vector3f to) {
        float length = from.distance(to);
        Vector3f p = new Vector3f();
        for (float d = 0.0F; d <= length; d += 0.4F) {
            from.lerp(to, d / length, p);
            int x = Mth.floor(p.x);
            int y = Mth.floor(p.y);
            int z = Mth.floor(p.z);
            if (!world.contains(x, y, z)) {
                if (!world.open || y < world.sizeY) {
                    return false;
                }
                continue;
            }
            if (!world.get(x, y, z).getCollisionShape(world, BlockPos.ZERO).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
