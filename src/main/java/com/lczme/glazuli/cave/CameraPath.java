package com.lczme.glazuli.cave;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * Flight paths for the background camera through the open air of a scene: a search over the block grid that prefers
 * cells with room all round, pulled taut wherever a straight stretch stays clear of every wall.
 */
final class CameraPath {
    /** How far the camera keeps from any block it can't pass through, along the straight stretches. */
    private static final float CLEARANCE = 0.4F;
    /** Moving through a cell with something solid next to it costs this much more, so paths keep to the middle. */
    private static final float CRAMPED_COST = 4.0F;
    private static final int MAX_EXPANDED = 400_000;
    private static final float WIDE_CLEARANCE = 1.2F;
    /** How far before and after a corner the curve round it starts and ends, at most. */
    private static final float CORNER_RADIUS = 9.0F;
    private static final int CORNER_SAMPLES = 16;
    private static final float ARC_HANDLE = 0.55F;
    private static final float[] PUSH_OUT = {0.75F, 1.5F, 2.5F, 3.5F, 5.0F};
    private static final float MERGE_DISTANCE = 3.0F;
    /** Curves tighter than this radius are flown slower, in proportion, up to {@link #MAX_SLOWDOWN} times. */
    private static final float PACE_RADIUS = 6.0F;
    private static final float MAX_SLOWDOWN = 3.0F;
    private static final float PACE_FADE = 3.0F;
    private static final int[][] STEPS = steps();

    private CameraPath() {
    }

    /**
     * From {@code from} to {@code to} with its corners rounded, passing first through {@code via} (if not null): where
     * a camera already on the move would carry on to, so it curves round onto the new path without a kink.
     */
    static List<Vector3f> find(CaveWorld world, Vector3f from, @Nullable Vector3f via, Vector3f to) {
        List<Vector3f> points = new ArrayList<>();
        if (via != null) {
            points.add(new Vector3f(from));
        }
        points.addAll(route(world, via != null ? via : from, to));
        return rounded(world, points);
    }

    /** From {@code from} to {@code to}; just the two points if the straight line is clear or no way round is found. */
    private static List<Vector3f> route(CaveWorld world, Vector3f from, Vector3f to) {
        List<Vector3f> straight = List.of(new Vector3f(from), new Vector3f(to));
        if (clear(world, from, to)) {
            return straight;
        }
        int[] start = nearestOpen(world, from);
        int[] goal = nearestOpen(world, to);
        if (start == null || goal == null) {
            return straight;
        }
        List<int[]> cells = search(world, start, goal);
        if (cells == null) {
            return straight;
        }
        List<Vector3f> points = new ArrayList<>();
        points.add(new Vector3f(from));
        for (int[] c : cells) {
            points.add(new Vector3f(c[0] + 0.5F, c[1] + 0.5F, c[2] + 0.5F));
        }
        points.add(new Vector3f(to));
        return taut(world, points);
    }

    /**
     * Replaces every corner with a curve that leaves the straight a few blocks before it and joins the next straight as
     * far after, so the camera turns smoothly instead of changing direction at once; a curve that would come too close
     * to a wall is drawn tighter.
     */
    private static List<Vector3f> rounded(CaveWorld world, List<Vector3f> points) {
        if (points.size() < 3) {
            return points;
        }
        points = merged(world, points);
        if (points.size() < 3) {
            return points;
        }
        List<Vector3f> corners = new ArrayList<>(points);
        // A corner hugging a wall edge has no room inside it for a curve: move it out, away from the edge, as far as
        // that widens the tightest turn among it and its neighbours.
        for (int i = 1; i < corners.size() - 1; i++) {
            Vector3f prev = corners.get(i - 1);
            Vector3f corner = corners.get(i);
            Vector3f next = corners.get(i + 1);
            Vector3f outward = new Vector3f(corner).sub(prev).normalize().sub(new Vector3f(next).sub(corner).normalize());
            if (outward.lengthSquared() < 1.0E-4F) {
                continue;
            }
            outward.normalize();
            Vector3f best = corner;
            float bestRadius = tightestTurn(world, corners, i);
            for (float push : PUSH_OUT) {
                Vector3f moved = new Vector3f(outward).mul(push).add(corner);
                if (!clear(world, prev, moved) || !clear(world, moved, next)) {
                    continue;
                }
                corners.set(i, moved);
                float radius = tightestTurn(world, corners, i);
                if (radius > bestRadius * 1.15F) {
                    best = moved;
                    bestRadius = radius;
                }
            }
            corners.set(i, best);
        }
        // Moving a corner changes the straights its neighbours turn between: put back any that leaves one unroundable.
        for (boolean changed = true; changed; ) {
            changed = false;
            for (int i = 1; i < corners.size() - 1; i++) {
                if (largestCurve(world, corners, i, corners.get(i)) > 0.0F) {
                    continue;
                }
                for (int j = Math.max(1, i - 1); j <= Math.min(corners.size() - 2, i + 1); j++) {
                    if (corners.get(j) != points.get(j)) {
                        corners.set(j, points.get(j));
                        changed = true;
                    }
                }
            }
        }
        List<Vector3f> out = new ArrayList<>();
        out.add(corners.get(0));
        for (int i = 1; i < corners.size() - 1; i++) {
            List<Vector3f> curve = null;
            for (float r = maxCurve(corners, i, corners.get(i)); r >= 0.3F && curve == null; r *= 0.8F) {
                curve = curve(world, corners.get(i - 1), corners.get(i), corners.get(i + 1), r);
            }
            if (curve == null) {
                out.add(corners.get(i));
            } else {
                out.addAll(curve);
            }
        }
        out.add(corners.get(corners.size() - 1));
        return out;
    }

    /**
     * How far before and after corner {@code i} (placed at {@code corner}) its curve may reach: a straight between two
     * corners is shared by both curves, one ending at a flight's end can be used whole.
     */
    private static float maxCurve(List<Vector3f> corners, int i, Vector3f corner) {
        float in = corners.get(i - 1).distance(corner) * (i == 1 ? 0.95F : 0.5F);
        float out = corner.distance(corners.get(i + 1)) * (i == corners.size() - 2 ? 0.95F : 0.5F);
        return Math.min(CORNER_RADIUS, Math.min(in, out));
    }

    /**
     * Two corners a few blocks apart make two tight curves where one turn was meant: where the straights either side
     * meet clear of walls, one corner there replaces both.
     */
    private static List<Vector3f> merged(CaveWorld world, List<Vector3f> points) {
        List<Vector3f> out = new ArrayList<>(points);
        for (int i = 1; i + 2 < out.size(); ) {
            Vector3f prev = out.get(i - 1);
            Vector3f c1 = out.get(i);
            Vector3f c2 = out.get(i + 1);
            Vector3f next = out.get(i + 2);
            Vector3f meet = c1.distance(c2) < MERGE_DISTANCE ? meeting(prev, c1, c2, next) : null;
            if (meet != null && clear(world, prev, meet) && clear(world, meet, next)) {
                out.set(i, meet);
                out.remove(i + 1);
            } else if (c1.distance(c2) < MERGE_DISTANCE && clear(world, prev, c2)) {
                out.remove(i);
            } else {
                i++;
            }
        }
        return out;
    }

    /** Where the line from {@code prev} through {@code c1} comes closest to the line from {@code next} back through {@code c2}, if ahead of both. */
    @Nullable
    private static Vector3f meeting(Vector3f prev, Vector3f c1, Vector3f c2, Vector3f next) {
        Vector3f d1 = new Vector3f(c1).sub(prev).normalize();
        Vector3f d2 = new Vector3f(c2).sub(next).normalize();
        Vector3f w = new Vector3f(c1).sub(c2);
        float b = d1.dot(d2);
        float denominator = 1.0F - b * b;
        if (denominator < 1.0E-3F) {
            return null;
        }
        float d = d1.dot(w);
        float e = d2.dot(w);
        float s = (b * e - d) / denominator;
        float t = (e - b * d) / denominator;
        if (s < 0.0F || t < 0.0F || s > 3.0F * MERGE_DISTANCE || t > 3.0F * MERGE_DISTANCE) {
            return null;
        }
        return new Vector3f(d1).mul(s).add(c1).add(new Vector3f(d2).mul(t).add(c2)).mul(0.5F);
    }

    /** The radius of the tightest of the turns at corner {@code i} and its neighbours, as they could be rounded now. */
    private static float tightestTurn(CaveWorld world, List<Vector3f> corners, int i) {
        float tightest = Float.MAX_VALUE;
        for (int j = Math.max(1, i - 1); j <= Math.min(corners.size() - 2, i + 1); j++) {
            float angle = new Vector3f(corners.get(j)).sub(corners.get(j - 1)).angle(new Vector3f(corners.get(j + 1)).sub(corners.get(j)));
            float reach = largestCurve(world, corners, j, corners.get(j));
            tightest = Math.min(tightest, angle < 1.0E-3F ? Float.MAX_VALUE : reach / (float) Math.tan(0.5F * angle));
        }
        return tightest;
    }

    private static float largestCurve(CaveWorld world, List<Vector3f> corners, int i, Vector3f corner) {
        for (float r = maxCurve(corners, i, corner); r >= 0.3F; r *= 0.8F) {
            if (curve(world, corners.get(i - 1), corner, corners.get(i + 1), r) != null) {
                return r;
            }
        }
        return 0.0F;
    }

    /** The curve round {@code corner} reaching {@code r} along each straight, or null if it comes too close to a wall. */
    @Nullable
    private static List<Vector3f> curve(CaveWorld world, Vector3f prev, Vector3f corner, Vector3f next, float r) {
        Vector3f a = new Vector3f(prev).sub(corner).normalize().mul(r).add(corner);
        Vector3f b = new Vector3f(next).sub(corner).normalize().mul(r).add(corner);
        // Control points part-way to the corner spread the turn evenly over the curve, close to a circular arc.
        Vector3f c1 = new Vector3f(a).lerp(corner, ARC_HANDLE);
        Vector3f c2 = new Vector3f(b).lerp(corner, ARC_HANDLE);
        List<Vector3f> curve = new ArrayList<>();
        for (int k = 0; k <= CORNER_SAMPLES; k++) {
            float t = (float) k / CORNER_SAMPLES;
            float u = 1.0F - t;
            curve.add(new Vector3f(a).mul(u * u * u).add(new Vector3f(c1).mul(3.0F * u * u * t))
                    .add(new Vector3f(c2).mul(3.0F * u * t * t)).add(new Vector3f(b).mul(t * t * t)));
            if (k > 0 && !clear(world, curve.get(k - 1), curve.get(k))) {
                return null;
            }
        }
        return curve;
    }

    static float length(List<Vector3f> path) {
        float total = 0.0F;
        for (int i = 1; i < path.size(); i++) {
            total += path.get(i - 1).distance(path.get(i));
        }
        return total;
    }

    /** The point {@code s} blocks along the path (clamped to its ends). */
    static Vector3f at(List<Vector3f> path, float s, Vector3f out) {
        for (int i = 1; i < path.size(); i++) {
            Vector3f a = path.get(i - 1);
            Vector3f b = path.get(i);
            float d = a.distance(b);
            if (s <= d || i == path.size() - 1) {
                return out.set(a).lerp(b, d > 0.0F ? Mth.clamp(s / d, 0.0F, 1.0F) : 1.0F);
            }
            s -= d;
        }
        return out.set(path.get(path.size() - 1));
    }

    /**
     * Per segment of {@code path}, how much slower than on the straight to fly it: tight curves are taken slowly (and
     * the slowdown eases in and out over a few blocks either side) so the camera never swings round at full speed.
     */
    static float[] pace(List<Vector3f> path) {
        int n = path.size();
        float[] slow = new float[n];
        for (int i = 1; i < n - 1; i++) {
            Vector3f in = new Vector3f(path.get(i)).sub(path.get(i - 1));
            Vector3f out = new Vector3f(path.get(i + 1)).sub(path.get(i));
            float shorter = Math.min(in.length(), out.length());
            if (shorter > 1.0E-4F) {
                float radius = shorter / Math.max(in.angle(out), 1.0E-4F);
                slow[i] = Mth.clamp(PACE_RADIUS / radius, 1.0F, MAX_SLOWDOWN);
            }
        }
        float[] along = new float[n];
        for (int i = 1; i < n; i++) {
            along[i] = along[i - 1] + path.get(i - 1).distance(path.get(i));
        }
        float[] weights = new float[Math.max(0, n - 1)];
        for (int j = 0; j < n - 1; j++) {
            float mid = 0.5F * (along[j] + along[j + 1]);
            float w = 1.0F;
            for (int i = 1; i < n - 1; i++) {
                float fade = 1.0F - Math.abs(along[i] - mid) / PACE_FADE;
                if (fade > 0.0F && slow[i] > 1.0F) {
                    w = Math.max(w, 1.0F + (slow[i] - 1.0F) * fade * fade * (3.0F - 2.0F * fade));
                }
            }
            weights[j] = w;
        }
        return weights;
    }

    static float pacedLength(List<Vector3f> path, float[] weights) {
        float total = 0.0F;
        for (int i = 1; i < path.size(); i++) {
            total += path.get(i - 1).distance(path.get(i)) * weights[i - 1];
        }
        return total;
    }

    /** The point {@code p} along the path measured in {@link #pace} weighted blocks (clamped to its ends). */
    static Vector3f atPaced(List<Vector3f> path, float[] weights, float p, Vector3f out) {
        for (int i = 1; i < path.size(); i++) {
            Vector3f a = path.get(i - 1);
            Vector3f b = path.get(i);
            float d = a.distance(b) * weights[i - 1];
            if (p <= d || i == path.size() - 1) {
                return out.set(a).lerp(b, d > 0.0F ? Mth.clamp(p / d, 0.0F, 1.0F) : 1.0F);
            }
            p -= d;
        }
        return out.set(path.get(path.size() - 1));
    }

    private static boolean passable(BlockState state) {
        return state.isAir() || state.getFluidState().isEmpty() && (state.canBeReplaced() || state.is(Blocks.CAVE_VINES)
                || state.is(Blocks.CAVE_VINES_PLANT) || state.is(Blocks.SPORE_BLOSSOM));
    }

    private static boolean open(CaveWorld world, int x, int y, int z) {
        return world.contains(x, y, z) && passable(world.get(x, y, z));
    }

    private static boolean open(CaveWorld world, float x, float y, float z) {
        return open(world, Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    private static boolean roomy(CaveWorld world, int x, int y, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (!open(world, x + dx, y + dy, z + dz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Whether a camera can fly straight from {@code a} to {@code b} keeping {@link #CLEARANCE} from anything solid. */
    static boolean clear(CaveWorld world, Vector3f a, Vector3f b) {
        return clear(world, a, b, CLEARANCE);
    }

    private static boolean clear(CaveWorld world, Vector3f a, Vector3f b, float r) {
        float d = a.distance(b);
        int samples = Math.max(1, Mth.ceil(d / 0.25F));
        Vector3f p = new Vector3f();
        for (int i = 0; i <= samples; i++) {
            a.lerp(b, (float) i / samples, p);
            if (!open(world, p.x, p.y, p.z) || !open(world, p.x + r, p.y, p.z) || !open(world, p.x - r, p.y, p.z)
                    || !open(world, p.x, p.y + r, p.z) || !open(world, p.x, p.y - r, p.z)
                    || !open(world, p.x, p.y, p.z + r) || !open(world, p.x, p.y, p.z - r)) {
                return false;
            }
        }
        return true;
    }

    private static int[] nearestOpen(CaveWorld world, Vector3f p) {
        int bx = Mth.floor(p.x);
        int by = Mth.floor(p.y);
        int bz = Mth.floor(p.z);
        for (int r = 0; r <= 3; r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) == r && open(world, bx + dx, by + dy, bz + dz)) {
                            return new int[]{bx + dx, by + dy, bz + dz};
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Weighted A* over the 26-connected block grid; cells as {x, y, z}, start excluded, goal included. */
    private static List<int[]> search(CaveWorld world, int[] start, int[] goal) {
        int sx = world.sizeX;
        int sy = world.sizeY;
        int sz = world.sizeZ;
        int count = sx * sy * sz;
        float[] cost = new float[count];
        Arrays.fill(cost, Float.POSITIVE_INFINITY);
        int[] from = new int[count];
        byte[] room = new byte[count];
        int startIndex = (start[1] * sz + start[2]) * sx + start[0];
        int goalIndex = (goal[1] * sz + goal[2]) * sx + goal[0];
        cost[startIndex] = 0.0F;
        from[startIndex] = -1;
        PriorityQueue<float[]> queue = new PriorityQueue<>((p, q) -> Float.compare(p[0], q[0]));
        queue.add(new float[]{heuristic(start[0], start[1], start[2], goal), startIndex});
        int expanded = 0;
        while (!queue.isEmpty() && expanded < MAX_EXPANDED) {
            float[] top = queue.poll();
            int index = (int) top[1];
            int x = index % sx;
            int z = index / sx % sz;
            int y = index / sx / sz;
            if (top[0] > cost[index] + heuristic(x, y, z, goal) + 1.0E-3F) {
                continue;
            }
            if (index == goalIndex) {
                List<int[]> cells = new ArrayList<>();
                for (int i = index; i != startIndex; i = from[i]) {
                    cells.add(0, new int[]{i % sx, i / sx / sz, i / sx % sz});
                }
                return cells;
            }
            expanded++;
            for (int[] step : STEPS) {
                int nx = x + step[0];
                int ny = y + step[1];
                int nz = z + step[2];
                if (!open(world, nx, ny, nz)) {
                    continue;
                }
                if (step[3] > 1 && (!open(world, x + step[0], y, z) || !open(world, x, y + step[1], z) || !open(world, x, y, z + step[2]))) {
                    continue;
                }
                int next = (ny * sz + nz) * sx + nx;
                if (room[next] == 0) {
                    room[next] = (byte) (roomy(world, nx, ny, nz) ? 1 : 2);
                }
                float c = cost[index] + Mth.sqrt(step[3]) * (room[next] == 1 ? 1.0F : CRAMPED_COST);
                if (c < cost[next]) {
                    cost[next] = c;
                    from[next] = index;
                    queue.add(new float[]{c + heuristic(nx, ny, nz, goal), next});
                }
            }
        }
        return null;
    }

    private static float heuristic(int x, int y, int z, int[] goal) {
        float dx = x - goal[0];
        float dy = y - goal[1];
        float dz = z - goal[2];
        return Mth.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Drops every point the path can skip by flying straight past it, keeping {@link #WIDE_CLEARANCE} from the walls
     * where there's room for it so the corners have space to be rounded generously.
     */
    private static List<Vector3f> taut(CaveWorld world, List<Vector3f> points) {
        List<Vector3f> out = new ArrayList<>();
        int i = 0;
        out.add(points.get(0));
        while (i < points.size() - 1) {
            int j = i + 1;
            float r = clear(world, points.get(i), points.get(j), WIDE_CLEARANCE) ? WIDE_CLEARANCE : CLEARANCE;
            while (j + 1 < points.size() && clear(world, points.get(i), points.get(j + 1), r)) {
                j++;
            }
            out.add(points.get(j));
            i = j;
        }
        return out;
    }

    /** The 26 neighbour offsets with their squared length. */
    private static int[][] steps() {
        List<int[]> steps = new ArrayList<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        steps.add(new int[]{dx, dy, dz, dx * dx + dy * dy + dz * dz});
                    }
                }
            }
        }
        return steps.toArray(new int[0][]);
    }
}
