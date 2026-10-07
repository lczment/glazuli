package com.lczme.liquidglass.cave;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.joml.Vector3f;

/** Menu scenes built around vanilla structures (and Create machinery when it is installed). */
final class BuiltScenes {
    static final String PLAINS_VILLAGE = "plains_village";
    static final String SNOWY_VILLAGE = "snowy_village";
    static final String DESERT_VILLAGE = "desert_village";
    static final String ANCIENT_CITY = "ancient_city";
    static final String TRIAL_CHAMBERS = "trial_chambers";
    static final String BASTION = "bastion";
    static final String END_CITY = "end_city";
    static final String CREATE_FACTORY = "create_factory";
    private static final List<String> STRUCTURES = List.of(PLAINS_VILLAGE, SNOWY_VILLAGE, DESERT_VILLAGE, ANCIENT_CITY,
            TRIAL_CHAMBERS, BASTION, END_CITY);

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int GROUND = 64;

    private BuiltScenes() {
    }

    static List<String> ids() {
        List<String> ids = new ArrayList<>(STRUCTURES);
        if (CreateCompat.isLoaded()) {
            ids.add(CREATE_FACTORY);
        }
        return ids;
    }

    static boolean has(String id) {
        return ids().contains(id);
    }

    static CaveWorld build(String id, long seed) throws IOException {
        return switch (id) {
            case PLAINS_VILLAGE -> village("plains", seed);
            case SNOWY_VILLAGE -> village("snowy", seed);
            case DESERT_VILLAGE -> village("desert", seed);
            case ANCIENT_CITY -> ancientCity(seed);
            case TRIAL_CHAMBERS -> trialChambers(seed);
            case BASTION -> bastion(seed);
            case END_CITY -> endCity(seed);
            case CREATE_FACTORY -> CreateFactory.build(seed);
            default -> throw new IllegalArgumentException("Unknown built scene " + id);
        };
    }

    // ---- terrain helpers -------------------------------------------------------------------------------------------

    private static StructureBuilder.Terrain flat(int ground, BlockState top, BlockState soil) {
        return new StructureBuilder.Terrain() {
            @Override
            public BlockState block(int x, int y, int z) {
                return y > ground ? AIR : y == ground ? top : soil;
            }

            @Override
            public int surface(int x, int z) {
                return ground + 1;
            }
        };
    }

    private static StructureBuilder.Terrain solid(BlockState state) {
        return new StructureBuilder.Terrain() {
            @Override
            public BlockState block(int x, int y, int z) {
                return state;
            }

            @Override
            public int surface(int x, int z) {
                return 0;
            }
        };
    }

    /**
     * Per column of {@code area}, the (Manhattan) distance to the nearest column holding a structure block at or above
     * {@code minY}: keeps trees off streets and lets hills rise only away from the buildings.
     */
    private static int[] footprintDistance(StructureBuilder builder, BoundingBox area, int minY) {
        int sx = area.getXSpan();
        int sz = area.getZSpan();
        int[] dist = new int[sx * sz];
        Arrays.fill(dist, Integer.MAX_VALUE);
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        for (Map.Entry<BlockPos, BlockState> entry : builder.blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getY() < minY || entry.getValue().isAir() || !area.isInside(pos)) {
                continue;
            }
            int i = (pos.getZ() - area.minZ()) * sx + pos.getX() - area.minX();
            if (dist[i] != 0) {
                dist[i] = 0;
                queue.enqueue(i);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.dequeueInt();
            int x = i % sx;
            int z = i / sx;
            int d = dist[i] + 1;
            if (x > 0 && dist[i - 1] > d) {
                dist[i - 1] = d;
                queue.enqueue(i - 1);
            }
            if (x < sx - 1 && dist[i + 1] > d) {
                dist[i + 1] = d;
                queue.enqueue(i + 1);
            }
            if (z > 0 && dist[i - sx] > d) {
                dist[i - sx] = d;
                queue.enqueue(i - sx);
            }
            if (z < sz - 1 && dist[i + sx] > d) {
                dist[i + sx] = d;
                queue.enqueue(i + sx);
            }
        }
        return dist;
    }

    /** Sets a block if it is inside the scene and the spot is free (air, plants, snow). */
    private static boolean put(CaveWorld world, int x, int y, int z, BlockState state) {
        if (!world.contains(x, y, z)) {
            return false;
        }
        BlockState old = world.get(x, y, z);
        if (!old.isAir() && !old.canBeReplaced()) {
            return false;
        }
        world.set(x, y, z, state);
        return true;
    }

    private static void tree(CaveWorld world, RandomSource random, int x, int y, int z, Block log, Block leaves) {
        BlockState leaf = leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int height = 4 + random.nextInt(3);
        for (int dy = height - 3; dy <= height + 1; dy++) {
            int r = dy >= height ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                    if (corner && (dy >= height || random.nextInt(2) == 0)) {
                        continue;
                    }
                    put(world, x + dx, y + dy, z + dz, leaf);
                }
            }
        }
        for (int dy = 0; dy < height; dy++) {
            world.set(x, y + dy, z, log.defaultBlockState());
        }
    }

    private static void spruce(CaveWorld world, RandomSource random, int x, int y, int z) {
        BlockState leaf = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int height = 7 + random.nextInt(4);
        for (int dy = height + 1; dy >= 2; dy--) {
            int layer = height + 1 - dy;
            // Alternating wide and narrow rings that widen downwards: the usual spruce silhouette.
            int r = layer == 0 ? 0 : layer % 2 == 1 ? Math.min(3, 1 + layer / 3) : Math.max(1, Math.min(2, layer / 3));
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r > 0 && Math.abs(dx) == r && Math.abs(dz) == r) {
                        continue;
                    }
                    put(world, x + dx, y + dy, z + dz, leaf);
                }
            }
        }
        for (int dy = 0; dy < height; dy++) {
            world.set(x, y + dy, z, Blocks.SPRUCE_LOG.defaultBlockState());
        }
    }

    /** A snow layer on every sky-facing surface that can hold one (roofs, leaves, fences don't). */
    private static void snowOver(CaveWorld world) {
        BlockState snow = Blocks.SNOW.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < world.sizeZ; z++) {
            for (int x = 0; x < world.sizeX; x++) {
                for (int y = world.sizeY - 2; y >= 0; y--) {
                    BlockState state = world.get(x, y, z);
                    if (state.isAir()) {
                        continue;
                    }
                    boolean holds = state.is(BlockTags.LEAVES) || state.isFaceSturdy(world, pos.set(x, y, z), Direction.UP)
                            && !state.is(Blocks.ICE) && !state.is(Blocks.PACKED_ICE) && !state.is(Blocks.SNOW);
                    if (holds && world.get(x, y + 1, z).isAir()) {
                        world.set(x, y + 1, z, snow);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Invisible light blocks on a loose grid through the open space between {@code minY} and {@code maxY}: a soft fill
     * light for scenes that are true to the game but too dark for a menu.
     */
    private static int fillLight(CaveWorld world, RandomSource random, int level, int spacing, int minY, int maxY) {
        int placed = 0;
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, level);
        for (int z = spacing / 2; z < world.sizeZ; z += spacing) {
            for (int y = Math.max(0, minY); y <= Math.min(world.sizeY - 1, maxY); y += spacing) {
                for (int x = spacing / 2; x < world.sizeX; x += spacing) {
                    int jx = x + random.nextInt(3) - 1;
                    int jz = z + random.nextInt(3) - 1;
                    if (world.contains(jx, y, jz) && world.get(jx, y, jz).isAir()) {
                        world.set(jx, y, jz, light);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    /** Lights candles and makes the scene's lighting match its blocks. */
    private static void finish(CaveWorld world) {
        for (int i = 0; i < world.blocks.length; i++) {
            BlockState state = world.blocks[i];
            if (state.getBlock() instanceof CandleBlock && !state.getValue(CandleBlock.WATERLOGGED)) {
                world.blocks[i] = state.setValue(CandleBlock.LIT, true);
            }
        }
        if (world.sky != null) {
            CaveGenerator.skyLight(world);
        }
        CaveGenerator.light(world);
    }

    // ---- camera ----------------------------------------------------------------------------------------------------

    private static boolean roomy(CaveWorld world, Vector3f eye) {
        int ex = Mth.floor(eye.x);
        int ey = Mth.floor(eye.y);
        int ez = Mth.floor(eye.z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = ex + dx;
                    int y = ey + dy;
                    int z = ez + dz;
                    if (!world.contains(x, y, z) || !world.get(x, y, z).getCollisionShape(world, BlockPos.ZERO).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static int obstruction(CaveWorld world, Vector3f from, Vector3f to) {
        float length = from.distance(to);
        int blocked = 0;
        for (float d = 1.0F; d < length - 2.0F; d += 0.5F) {
            float t = d / length;
            int x = Mth.floor(Mth.lerp(t, from.x, to.x));
            int y = Mth.floor(Mth.lerp(t, from.y, to.y));
            int z = Mth.floor(Mth.lerp(t, from.z, to.z));
            if (CaveWorld.isOpaqueCube(world.get(x, y, z))) {
                blocked++;
            }
        }
        return blocked;
    }

    /**
     * Places the camera {@code distance} away from {@code target} and {@code height} above it, looking at it from the
     * first direction (starting at {@code yaw}, then fanning out) with a clear view and room around the eye.
     */
    private static void aim(CaveWorld world, Vector3f target, float distance, float height, float yaw) {
        Vector3f bestEye = null;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < 24 && best > 0.0F; i++) {
            float turn = yaw + (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * 15.0F;
            float rad = turn * Mth.DEG_TO_RAD;
            for (float f : new float[]{1.0F, 0.8F, 0.62F, 0.48F, 0.36F}) {
                Vector3f eye = new Vector3f(target.x + Mth.sin(rad) * distance * f, target.y + height * f,
                        target.z - Mth.cos(rad) * distance * f);
                if (!roomy(world, eye)) {
                    continue;
                }
                float score = obstruction(world, eye, target) * 4.0F + (1.0F - f) * 3.0F;
                if (score < best) {
                    best = score;
                    bestEye = eye;
                }
                break;
            }
        }
        if (bestEye == null) {
            bestEye = new Vector3f(target).add(0.0F, 2.0F, 0.0F);
        }
        float dx = target.x - bestEye.x;
        float dy = target.y - bestEye.y;
        float dz = target.z - bestEye.z;
        float lookYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float lookPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        CustomRig rig = new CustomRig(bestEye, lookYaw, lookPitch);
        rig.bind(world);
        world.rig = rig;
    }

    /** A floor 2 to 4 blocks below: eye height of someone standing or on a low ledge. */
    private static boolean nearFloor(CaveWorld world, int x, int y, int z) {
        for (int d = 2; d <= 4; d++) {
            if (!world.get(x, y - d, z).getCollisionShape(world, BlockPos.ZERO).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** How far a ray gets through open space, up to {@code max} blocks. */
    private static float rayLength(CaveWorld world, Vector3f from, float yaw, float pitch, float max) {
        float y = yaw * Mth.DEG_TO_RAD;
        float p = pitch * Mth.DEG_TO_RAD;
        float dx = -Mth.sin(y) * Mth.cos(p);
        float dy = -Mth.sin(p);
        float dz = Mth.cos(y) * Mth.cos(p);
        for (float d = 1.0F; d < max; d += 1.0F) {
            int x = Mth.floor(from.x + dx * d);
            int yy = Mth.floor(from.y + dy * d);
            int z = Mth.floor(from.z + dz * d);
            if (!world.contains(x, yy, z) || CaveWorld.isOpaqueCube(world.get(x, yy, z))) {
                return d;
            }
        }
        return max;
    }

    /**
     * For enclosed scenes without one obvious subject: tries eyes on a grid through {@code region} (scene
     * coordinates) and keeps the eye and heading that see deepest across a fan of rays, so the camera ends up looking
     * down the longest hall rather than at a wall.
     */
    private static void aimDeepest(CaveWorld world, BoundingBox region, float pitch) {
        Vector3f best = null;
        float bestYaw = 0.0F;
        float bestScore = -1.0F;
        Vector3f eye = new Vector3f();
        for (int z = region.minZ(); z <= region.maxZ(); z += 3) {
            for (int y = region.minY(); y <= region.maxY(); y += 3) {
                for (int x = region.minX(); x <= region.maxX(); x += 3) {
                    eye.set(x + 0.5F, y + 0.5F, z + 0.5F);
                    if (!roomy(world, eye) || !nearFloor(world, x, y, z)) {
                        continue;
                    }
                    for (int yaw = 0; yaw < 360; yaw += 20) {
                        float score = 0.0F;
                        for (int turn = -30; turn <= 30; turn += 30) {
                            for (int tilt = -5; tilt <= 15; tilt += 20) {
                                score += rayLength(world, eye, yaw + turn, pitch + tilt, 40.0F);
                            }
                        }
                        if (score > bestScore) {
                            bestScore = score;
                            best = new Vector3f(eye);
                            bestYaw = yaw;
                        }
                    }
                }
            }
        }
        if (best == null) {
            best = new Vector3f(region.getCenter().getX(), region.getCenter().getY(), region.getCenter().getZ());
        }
        CustomRig rig = new CustomRig(best, bestYaw, pitch);
        rig.bind(world);
        world.rig = rig;
    }

    private static Vector3f center(BoundingBox box, BoundingBox area, float y) {
        return new Vector3f((box.minX() + box.maxX() + 1) * 0.5F - area.minX(), y,
                (box.minZ() + box.maxZ() + 1) * 0.5F - area.minZ());
    }

    // ---- villages --------------------------------------------------------------------------------------------------

    private static CaveWorld village(String kind, long seed) throws IOException {
        boolean snowy = kind.equals("snowy");
        boolean desert = kind.equals("desert");
        BlockState top = desert ? Blocks.SAND.defaultBlockState()
                : Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyDirtBlock.SNOWY, snowy);
        BlockState soil = desert ? Blocks.SANDSTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        StructureBuilder builder = StructureBuilder.create(flat(GROUND, top, soil));
        builder.jigsaw("village/" + kind + "/town_centers", null, 5, new BlockPos(0, GROUND, 0), true, true, 80, seed);
        BoundingBox s = builder.bounds();
        int margin = 26;
        BoundingBox area = new BoundingBox(s.minX() - margin, GROUND - 6, s.minZ() - margin,
                s.maxX() + margin, Math.max(s.maxY(), GROUND + 16) + 16, s.maxZ() + margin);
        int sx = area.getXSpan();
        int ground = GROUND - area.minY();
        int[] dist = footprintDistance(builder, area, GROUND);
        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed));
        int[] height = new int[dist.length];
        for (int i = 0; i < height.length; i++) {
            double n = noise.noise((i % sx) * 0.035, 0.0, (i / sx) * 0.035);
            double rise = Math.min(Math.max(0, dist[i] - 6) * 0.42, 15.0) * (0.6 + 0.5 * n);
            height[i] = ground + (int) Math.round(Math.max(0.0, rise));
        }
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        SceneLook look = switch (kind) {
            case "snowy" -> SceneLook.outdoor(0x0A1022, 160.0F, 0.12F, 0x80B497, 0x60A17B, 18000L);
            case "desert" -> SceneLook.outdoor(0xE6D3A8, 200.0F, 1.0F, 0xBFB755, 0xAEA42A, 3500L);
            default -> SceneLook.outdoor(0xF0A868, 180.0F, 0.75F, 0x91BD59, 0x77AB2F, 12400L);
        };
        CaveWorld world = builder.toWorld(area, true, look, (x, y, z) -> {
            int h = height[z * sx + x];
            if (y > h) {
                return AIR;
            }
            if (y == h) {
                return top;
            }
            if (y < h - 3) {
                return desert ? Blocks.SANDSTONE.defaultBlockState() : stone;
            }
            return desert ? sand : soil;
        });
        RandomSource random = RandomSource.create(seed ^ 0x5EEDL);
        for (int z = 2; z < area.getZSpan() - 2; z++) {
            for (int x = 2; x < sx - 2; x++) {
                int i = z * sx + x;
                int y = height[i];
                if (dist[i] < 2 || world.get(x, y, z) != top || !world.get(x, y + 1, z).isAir()) {
                    continue;
                }
                float roll = random.nextFloat();
                if (desert) {
                    if (dist[i] >= 4 && roll < 0.007F) {
                        int tall = 1 + random.nextInt(3);
                        for (int dy = 1; dy <= tall; dy++) {
                            put(world, x, y + dy, z, Blocks.CACTUS.defaultBlockState());
                        }
                    } else if (roll < 0.018F) {
                        put(world, x, y + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                    }
                } else if (snowy) {
                    if (dist[i] >= 5 && roll < 0.02F) {
                        spruce(world, random, x, y + 1, z);
                    }
                } else if (dist[i] >= 5 && roll < 0.018F) {
                    boolean birch = random.nextInt(4) == 0;
                    tree(world, random, x, y + 1, z, birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG, birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES);
                } else if (roll < 0.28F) {
                    put(world, x, y + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                } else if (roll < 0.31F) {
                    Block[] flowers = {Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET};
                    put(world, x, y + 1, z, flowers[random.nextInt(flowers.length)].defaultBlockState());
                }
            }
        }
        if (snowy) {
            snowOver(world);
        }
        finish(world);
        aim(world, center(builder.startBox(), area, ground + 3.0F), 24.0F, 9.0F, 0.0F);
        return world;
    }

    // ---- underground -----------------------------------------------------------------------------------------------

    /** The y of the most common walkable surface (solid with free space above) inside {@code box}. */
    private static int floorLevel(StructureBuilder builder, BoundingBox box) {
        int[] counts = new int[box.getYSpan()];
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (Map.Entry<BlockPos, BlockState> entry : builder.blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!box.isInside(pos) || !entry.getValue().isSolidRender(builder.reader(), pos)) {
                continue;
            }
            BlockState up = builder.blocks.get(above.setWithOffset(pos, Direction.UP));
            if (up != null && up.isAir()) {
                counts[pos.getY() - box.minY()]++;
            }
        }
        int best = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        return box.minY() + best;
    }

    private static CaveWorld ancientCity(long seed) throws IOException {
        StructureBuilder builder = StructureBuilder.create(solid(Blocks.DEEPSLATE.defaultBlockState()));
        builder.jigsaw("ancient_city/city_center", "minecraft:city_anchor", 3, new BlockPos(0, -27, 0), false, false, 116, seed);
        BoundingBox start = builder.startBox();
        BoundingBox s = builder.bounds();
        int floor = floorLevel(builder, start);
        int cx = (start.minX() + start.maxX()) / 2;
        int cz = (start.minZ() + start.maxZ()) / 2;
        int half = 58;
        BoundingBox area = new BoundingBox(Math.max(s.minX() - 4, cx - half), floor - 8, Math.max(s.minZ() - 4, cz - half),
                Math.min(s.maxX() + 4, cx + half), Math.max(start.maxY(), floor + 20) + 10, Math.min(s.maxZ() + 4, cz + half));
        int sx = area.getXSpan();
        int sz = area.getZSpan();
        int sy = area.getYSpan();
        int f = floor - area.minY();
        float ccx = cx - area.minX();
        float ccz = cz - area.minZ();
        float rx = sx * 0.5F - 3.0F;
        float rz = sz * 0.5F - 3.0F;
        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed));
        RandomSource random = RandomSource.create(seed ^ 0xC17EL);
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        BlockState sculk = Blocks.SCULK.defaultBlockState();
        SceneLook look = SceneLook.underground(0x05080C, 80.0F, 0.55F, 0.75F, 1.00F, 0.05F, 0.07F, 0.10F);
        CaveWorld world = builder.toWorld(area, false, look, (x, y, z) -> {
            float u = (x - ccx) / rx;
            float v = (z - ccz) / rz;
            float r2 = u * u + v * v;
            double n = noise.noise(x * 0.08, y * 0.08, z * 0.08);
            if (y < f) {
                return deepslate;
            }
            if (y == f) {
                return n > -0.15 ? sculk : deepslate;
            }
            float ceiling = f + 5 + (sy - f - 8) * Mth.sqrt(Math.max(0.0F, 1.0F - r2)) + (float) n * 4.0F;
            return r2 + n * 0.1 < 1.0 && y < ceiling ? AIR : deepslate;
        });
        BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState();
        for (int z = 1; z < sz - 1; z++) {
            for (int y = f + 1; y < sy - 1; y++) {
                for (int x = 1; x < sx - 1; x++) {
                    if (!world.get(x, y, z).isAir() || random.nextFloat() > 0.09F) {
                        continue;
                    }
                    for (Direction direction : Direction.values()) {
                        BlockState next = world.get(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
                        if (next == deepslate) {
                            world.set(x, y, z, lichen.setValue(MultifaceBlock.getFaceProperty(direction), true));
                            break;
                        }
                    }
                }
            }
        }
        // Shader packs light by visible emitters only: soul lanterns hang from the cavern roof. The invisible fill
        // light is for vanilla rendering.
        BlockState chain = Blocks.CHAIN.defaultBlockState();
        BlockState soulLantern = Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
        for (int z = 4; z < sz - 4; z += 9) {
            for (int x = 4; x < sx - 4; x += 9) {
                int lx = x + random.nextInt(5) - 2;
                int lz = z + random.nextInt(5) - 2;
                for (int y = sy - 2; y > f + 8; y--) {
                    if (!world.get(lx, y, lz).isAir() || !CaveWorld.isOpaqueCube(world.get(lx, y + 1, lz))) {
                        continue;
                    }
                    int length = 3 + random.nextInt(8);
                    boolean clear = true;
                    for (int d = 0; d <= length + 5; d++) {
                        clear &= world.get(lx, y - d, lz).isAir();
                    }
                    if (clear) {
                        for (int d = 0; d < length; d++) {
                            world.set(lx, y - d, lz, chain);
                        }
                        world.set(lx, y - length, lz, soulLantern);
                    }
                    break;
                }
            }
        }
        BlockState standing = Blocks.SOUL_LANTERN.defaultBlockState();
        for (int z = 1; z < sz - 1; z++) {
            for (int x = 1; x < sx - 1; x++) {
                for (int y = f + 1; y < Math.min(sy - 1, f + 14); y++) {
                    if (world.get(x, y, z).isAir() && CaveWorld.isOpaqueCube(world.get(x, y - 1, z))
                            && world.get(x, y + 1, z).isAir() && random.nextFloat() < 0.012F) {
                        world.set(x, y, z, standing);
                    }
                }
            }
        }
        fillLight(world, random, 8, 7, f + 2, f + 16);
        finish(world);
        aim(world, new Vector3f(ccx, f + 9.0F, ccz), 30.0F, 3.0F, 0.0F);
        return world;
    }

    private static CaveWorld trialChambers(long seed) throws IOException {
        StructureBuilder builder = StructureBuilder.create(solid(Blocks.TUFF.defaultBlockState()));
        builder.jigsaw("trial_chambers/chamber/end", null, 8, new BlockPos(0, -30, 0), false, false, 116, seed);
        BoundingBox start = builder.startBox();
        BoundingBox s = builder.bounds();
        int half = 48;
        int cx = (start.minX() + start.maxX()) / 2;
        int cz = (start.minZ() + start.maxZ()) / 2;
        BoundingBox area = new BoundingBox(Math.max(s.minX() - 2, cx - half), Math.max(s.minY() - 2, start.minY() - 24),
                Math.max(s.minZ() - 2, cz - half), Math.min(s.maxX() + 2, cx + half), Math.min(s.maxY() + 2, start.maxY() + 24),
                Math.min(s.maxZ() + 2, cz + half));
        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed));
        BlockState tuff = Blocks.TUFF.defaultBlockState();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        SceneLook look = SceneLook.underground(0x0B0D10, 70.0F, 1.00F, 0.80F, 0.55F, 0.06F, 0.06F, 0.07F);
        CaveWorld world = builder.toWorld(area, false, look,
                (x, y, z) -> noise.noise(x * 0.06, y * 0.06, z * 0.06) > 0.1 ? deepslate : tuff);
        finish(world);
        aimDeepest(world, new BoundingBox(4, 4, 4, world.sizeX - 5, world.sizeY - 5, world.sizeZ - 5), 12.0F);
        return world;
    }

    private static CaveWorld bastion(long seed) throws IOException {
        int lavaLevel = 31;
        StructureBuilder builder = StructureBuilder.create(solid(AIR));
        builder.jigsaw("bastion/starts", null, 6, new BlockPos(0, 33, 0), false, false, 80, seed);
        BoundingBox s = builder.bounds();
        int margin = 28;
        BoundingBox area = new BoundingBox(s.minX() - margin, Math.min(s.minY(), lavaLevel) - 6, s.minZ() - margin,
                s.maxX() + margin, s.maxY() + 26, s.maxZ() + margin);
        int sx = area.getXSpan();
        int sz = area.getZSpan();
        int sy = area.getYSpan();
        int lava = lavaLevel - area.minY();
        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed));
        BlockState netherrack = Blocks.NETHERRACK.defaultBlockState();
        BlockState lavaBlock = Blocks.LAVA.defaultBlockState();
        BlockState basalt = Blocks.BASALT.defaultBlockState();
        float hx = sx * 0.5F;
        float hz = sz * 0.5F;
        // An overworld cavern rather than the Nether: shader packs without Nether programs stamp a warning on it.
        SceneLook look = SceneLook.underground(0x3A0C06, 140.0F, 1.00F, 0.70F, 0.40F, 0.12F, 0.05F, 0.03F);
        CaveWorld world = builder.toWorld(area, false, look, (x, y, z) -> {
            float u = (x - hx) / (hx - 2.0F);
            float v = (z - hz) / (hz - 2.0F);
            double n = noise.noise(x * 0.05, y * 0.05, z * 0.05);
            double r2 = u * u + v * v + n * 0.18 - Math.max(0, y - lava) * 0.004;
            if (y < lava - 3) {
                return netherrack;
            }
            if (r2 > 1.0 || y > sy - 6 + n * 4.0) {
                return n > 0.35 ? basalt : netherrack;
            }
            return y <= lava ? lavaBlock : AIR;
        });
        RandomSource random = RandomSource.create(seed ^ 0xB45L);
        for (int z = 1; z < sz - 1; z++) {
            for (int x = 1; x < sx - 1; x++) {
                for (int y = sy - 2; y > lava; y--) {
                    if (world.get(x, y, z).isAir()) {
                        if (world.get(x, y + 1, z) == netherrack && random.nextFloat() < 0.06F) {
                            int drop = 1 + random.nextInt(3);
                            for (int d = 0; d < drop; d++) {
                                put(world, x, y - d, z, Blocks.GLOWSTONE.defaultBlockState());
                            }
                        }
                        break;
                    }
                }
            }
        }
        finish(world);
        float target = s.minY() - area.minY() + s.getYSpan() * 0.45F;
        aim(world, center(s, area, target), Math.max(s.getXSpan(), s.getZSpan()) * 0.62F, s.getYSpan() * 0.35F + 4.0F, 0.0F);
        return world;
    }

    // ---- the end ---------------------------------------------------------------------------------------------------

    private static boolean hasShip(StructureBuilder builder) {
        return builder.blocks.values().stream().anyMatch(state -> state.is(Blocks.DRAGON_HEAD) || state.is(Blocks.DRAGON_WALL_HEAD));
    }

    private static CaveWorld endCity(long seed) throws IOException {
        int top = 60;
        StructureBuilder builder = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            builder = StructureBuilder.create(solid(AIR));
            builder.endCity(new BlockPos(0, top + 1, 0), Rotation.getRandom(RandomSource.create(seed + attempt)), seed + attempt);
            if (hasShip(builder)) {
                break;
            }
        }
        BoundingBox s = builder.bounds();
        float cx = (s.minX() + s.maxX()) * 0.5F;
        float cz = (s.minZ() + s.maxZ()) * 0.5F;
        float radius = 0.0F;
        for (BlockPos pos : builder.blocks.keySet()) {
            if (pos.getY() <= top + 3) {
                radius = Math.max(radius, Mth.sqrt((pos.getX() - cx) * (pos.getX() - cx) + (pos.getZ() - cz) * (pos.getZ() - cz)));
            }
        }
        float island = Math.max(40.0F, radius + 14.0F);
        int depth = 34;
        BoundingBox area = new BoundingBox(
                Math.min(s.minX(), Mth.floor(cx - island)) - 8, top - depth - 2, Math.min(s.minZ(), Mth.floor(cz - island)) - 8,
                Math.max(s.maxX(), Mth.ceil(cx + island)) + 8, s.maxY() + 8, Math.max(s.maxZ(), Mth.ceil(cz + island)) + 8);
        int sx = area.getXSpan();
        int sz = area.getZSpan();
        int t = top - area.minY();
        float icx = cx - area.minX();
        float icz = cz - area.minZ();
        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed));
        BlockState endStone = Blocks.END_STONE.defaultBlockState();
        // A night sky rather than the End's: shader packs without End programs stamp a warning on it.
        SceneLook look = new SceneLook(0x0D0A16, 60.0F, 220.0F, 0.95F, 0.85F, 1.00F, 0.16F, 0.13F, 0.20F, 0.10F,
                SceneLook.WATER, SceneLook.GRASS, SceneLook.FOLIAGE, 13000L, SceneLook.Dimension.OVERWORLD);
        CaveWorld world = builder.toWorld(area, true, look, (x, y, z) -> {
            float dx = x - icx;
            float dz = z - icz;
            double n = noise.noise(x * 0.06, 0.0, z * 0.06);
            float r = Mth.sqrt(dx * dx + dz * dz) / (island * (float) (0.9 + 0.12 * n));
            if (r >= 1.0F) {
                return AIR;
            }
            int surface = t - (r > 0.75F ? (int) ((r - 0.75F) * 12.0F) : 0);
            int bottom = t - (int) (depth * (1.0F - r * r)) + (int) (noise.noise(x * 0.2, 7.0, z * 0.2) * 3.0);
            return y <= surface && y >= bottom ? endStone : AIR;
        });
        world.voidOutside = true;
        int[] dist = footprintDistance(builder, area, top + 1);
        RandomSource random = RandomSource.create(seed ^ 0xE4DL);
        for (int z = 1; z < sz - 1; z++) {
            for (int x = 1; x < sx - 1; x++) {
                if (dist[z * sx + x] < 4 || random.nextFloat() > 0.012F || world.get(x, t, z) != endStone) {
                    continue;
                }
                chorus(world, random, x, t + 1, z);
            }
        }
        finish(world);
        float targetY = (s.minY() + s.maxY()) * 0.5F - area.minY();
        aim(world, center(s, area, targetY), Math.max(s.getXSpan(), s.getZSpan()) * 0.35F + s.getYSpan() * 0.6F, 0.0F, 0.0F);
        return world;
    }

    private static void chorus(CaveWorld world, RandomSource random, int x, int y, int z) {
        int height = 2 + random.nextInt(4);
        BlockState stem = Blocks.CHORUS_PLANT.defaultBlockState();
        for (int dy = 0; dy < height; dy++) {
            BlockState state = stem.setValue(PipeBlock.DOWN, true).setValue(PipeBlock.UP, true);
            if (dy == height - 2 && random.nextBoolean()) {
                Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                if (put(world, x + side.getStepX(), y + dy, z + side.getStepZ(),
                        Blocks.CHORUS_FLOWER.defaultBlockState().setValue(ChorusFlowerBlock.AGE, 5))) {
                    state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), true);
                }
            }
            put(world, x, y + dy, z, state);
        }
        put(world, x, y + height, z, Blocks.CHORUS_FLOWER.defaultBlockState().setValue(ChorusFlowerBlock.AGE, 5));
    }
}
