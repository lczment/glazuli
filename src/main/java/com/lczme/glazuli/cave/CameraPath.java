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
    /** How far before and after a corner the curve round it starts and ends, at most. */
    private static final float CORNER_RADIUS = 7.0F;
    private static final int CORNER_SAMPLES = 16;
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
        List<Vector3f> out = new ArrayList<>();
        out.add(points.get(0));
        for (int i = 1; i < points.size() - 1; i++) {
            Vector3f prev = points.get(i - 1);
            Vector3f corner = points.get(i);
            Vector3f next = points.get(i + 1);
            float in = prev.distance(corner);
            float outLength = corner.distance(next);
            Vector3f dirIn = new Vector3f(corner).sub(prev).div(Math.max(in, 1.0E-4F));
            Vector3f dirOut = new Vector3f(next).sub(corner).div(Math.max(outLength, 1.0E-4F));
            List<Vector3f> curve = null;
            for (float r = Math.min(CORNER_RADIUS, 0.5F * Math.min(in, outLength)); r >= 0.3F && curve == null; r *= 0.6F) {
                Vector3f a = new Vector3f(dirIn).mul(-r).add(corner);
                Vector3f b = new Vector3f(dirOut).mul(r).add(corner);
                List<Vector3f> candidate = new ArrayList<>();
                for (int k = 0; k <= CORNER_SAMPLES; k++) {
                    float t = (float) k / CORNER_SAMPLES;
                    float u = 1.0F - t;
                    // Cubic with both inner control points on the corner: no curvature where it meets the straights.
                    candidate.add(new Vector3f(a).mul(u * u * u).add(new Vector3f(corner).mul(3.0F * u * t))
                            .add(new Vector3f(b).mul(t * t * t)));
                }
                boolean ok = true;
                for (int k = 1; k < candidate.size() && ok; k++) {
                    ok = clear(world, candidate.get(k - 1), candidate.get(k));
                }
                if (ok) {
                    curve = candidate;
                }
            }
            if (curve == null) {
                out.add(corner);
            } else {
                out.addAll(curve);
            }
        }
        out.add(points.get(points.size() - 1));
        return out;
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
        float d = a.distance(b);
        int samples = Math.max(1, Mth.ceil(d / 0.25F));
        Vector3f p = new Vector3f();
        float r = CLEARANCE;
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

    /** Drops every point the path can skip by flying straight past it. */
    private static List<Vector3f> taut(CaveWorld world, List<Vector3f> points) {
        List<Vector3f> out = new ArrayList<>();
        int i = 0;
        out.add(points.get(0));
        while (i < points.size() - 1) {
            int j = i + 1;
            while (j + 1 < points.size() && clear(world, points.get(i), points.get(j + 1))) {
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
