package com.lczme.glazuli.dev;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * Development-only section timer for {@code -Dglazuli.bench=true}: CPU time with {@link System#nanoTime()} for every
 * section, GPU time with {@code GL_TIME_ELAPSED} queries (read back a few frames later) for the leaf sections, which
 * never nest in each other. {@link #ON} is a constant, so the calls compile away in normal play.
 */
public final class Perf {
    public static final boolean ON = Boolean.getBoolean("glazuli.bench");

    public static final int FRAME = 0;
    public static final int CAPTURE = 1;
    public static final int PANEL = 2;
    public static final int CAVE = 3;
    public static final int SHADER_CAVE = 4;
    public static final int CONTAINER = 5;
    public static final int HUD = 6;
    public static final int SCREEN = 7;
    public static final int FADE = 8;
    static final String[] NAMES = {"frame", "capture", "panel", "cave", "shaderCave", "container", "hud", "screen", "fade"};
    private static final boolean[] LEAF = {false, true, true, false, true, true, false, false, true};
    private static final int SECTIONS = NAMES.length;
    private static final int LATENCY = 4;

    private static final long[] cpuStart = new long[SECTIONS];
    private static final int[] depth = new int[SECTIONS];
    static final long[] cpuTotal = new long[SECTIONS];
    static final long[] cpuFrame = new long[SECTIONS];
    static final long[] gpuTotal = new long[SECTIONS];
    static final long[] calls = new long[SECTIONS];

    private static final ArrayDeque<Integer> freeQueries = new ArrayDeque<>();
    private static final ArrayDeque<List<int[]>> pending = new ArrayDeque<>();
    private static List<int[]> current = new ArrayList<>();
    private static int openLeaf = -1;
    private static int openQuery;

    private Perf() {
    }

    public static void begin(int section) {
        if (!ON || depth[section]++ > 0) {
            return;
        }
        cpuStart[section] = System.nanoTime();
        if (LEAF[section] && openLeaf < 0) {
            openLeaf = section;
            openQuery = freeQueries.isEmpty() ? GL15.glGenQueries() : freeQueries.pop();
            GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, openQuery);
        }
    }

    public static void end(int section) {
        if (!ON || depth[section] == 0 || --depth[section] > 0) {
            return;
        }
        long cpu = System.nanoTime() - cpuStart[section];
        cpuTotal[section] += cpu;
        cpuFrame[section] += cpu;
        calls[section]++;
        if (openLeaf == section) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            current.add(new int[]{section, openQuery});
            openLeaf = -1;
        }
    }

    /** Called once per frame after it is submitted: queues this frame's queries, reads back an older frame's. */
    static void endFrame(boolean record) {
        pending.add(current);
        current = new ArrayList<>();
        while (pending.size() > LATENCY) {
            for (int[] q : pending.poll()) {
                if (record) {
                    gpuTotal[q[0]] += GL33.glGetQueryObjecti64(q[1], GL15.GL_QUERY_RESULT);
                }
                freeQueries.push(q[1]);
            }
        }
        Arrays.fill(depth, 0);
        if (openLeaf >= 0) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            freeQueries.push(openQuery);
            openLeaf = -1;
        }
    }

    static void startFrame() {
        Arrays.fill(cpuFrame, 0L);
    }

    static void reset() {
        Arrays.fill(cpuTotal, 0L);
        Arrays.fill(gpuTotal, 0L);
        Arrays.fill(calls, 0L);
    }
}
