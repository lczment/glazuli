package com.lczme.liquidglass.cave;

import static com.lczme.liquidglass.cave.CaveWorld.SIZE_X;
import static com.lczme.liquidglass.cave.CaveWorld.SIZE_Y;
import static com.lczme.liquidglass.cave.CaveWorld.SIZE_Z;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/** Carves, paints, decorates and lights the background cave. Pure data, safe to run off the render thread. */
public final class CaveGenerator {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    private CaveGenerator() {
    }

    public static CaveWorld generate(long seed) {
        CaveLayout layout = new CaveLayout();
        CaveWorld world = new CaveWorld(layout);
        RandomSource random = RandomSource.create(seed);
        ImprovedNoise shape = new ImprovedNoise(random);
        ImprovedNoise paint = new ImprovedNoise(random);
        carve(world, layout, shape);
        paint(world, layout, paint, random);
        decorate(world, layout, random);
        lightIntroPath(world, layout);
        light(world);
        return world;
    }

    /** The startup flight is revealed through the logo, so its tunnel needs dependable light, not just random torches. */
    private static void lightIntroPath(CaveWorld world, CaveLayout layout) {
        int side = 2;
        for (int z = (int) CaveLayout.TUNNEL_START_Z + 4; z < CaveLayout.CHAMBER_Z - CaveLayout.CHAMBER_RZ; z += 9) {
            side = -side;
            int x = Math.round(CaveLayout.centerX(z)) + side;
            int y = Math.round(CaveLayout.centerY(z));
            while (y < CaveWorld.SIZE_Y - 1 && world.get(x, y + 1, z).isAir()) {
                y++;
            }
            if (world.get(x, y, z).isAir() && !world.get(x, y + 1, z).isAir()) {
                world.set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
    }

    private static double chamberDistance(CaveLayout layout, int x, int y, int z) {
        double ex = (x + 0.5 - layout.chamberX) / CaveLayout.CHAMBER_RX;
        double ey = (y + 0.5 - layout.chamberY) / CaveLayout.CHAMBER_RY;
        double ez = (z + 0.5 - CaveLayout.CHAMBER_Z) / CaveLayout.CHAMBER_RZ;
        return ex * ex + ey * ey + ez * ez;
    }

    private static boolean inPool(CaveLayout layout, int x, int z, double n) {
        double px = (x + 0.5 - layout.chamberX - 1.0) / 8.0;
        double pz = (z + 0.5 - CaveLayout.CHAMBER_Z - 4.0) / 6.0;
        return px * px + pz * pz < 1.0 + 0.2 * n;
    }

    private static void carve(CaveWorld world, CaveLayout layout, ImprovedNoise noise) {
        for (int z = 0; z < SIZE_Z; z++) {
            float cx = CaveLayout.centerX(z + 0.5F);
            float cy = CaveLayout.centerY(z + 0.5F);
            float r = CaveLayout.tunnelRadius(z + 0.5F);
            for (int y = 0; y < SIZE_Y; y++) {
                for (int x = 0; x < SIZE_X; x++) {
                    BlockState state = STONE;
                    boolean interior = x > 1 && y > 1 && z > 1 && x < SIZE_X - 2 && y < SIZE_Y - 2 && z < SIZE_Z - 2;
                    if (interior) {
                        double n = noise.noise(x * 0.11, y * 0.13, z * 0.11);
                        double dx = x + 0.5 - cx;
                        double dy = (y + 0.5 - cy) / 0.8;
                        double rr = r + 1.6 * n;
                        boolean tunnel = dx * dx + dy * dy < rr * rr && y + 0.5 > cy - r * 0.62;
                        boolean chamber = chamberDistance(layout, x, y, z) < 1.0 + 0.35 * n && y >= layout.floorY;
                        boolean pool = (y == layout.floorY - 1 || y == layout.floorY - 2) && inPool(layout, x, z, n)
                                && chamberDistance(layout, x, layout.floorY, z) < 0.8;
                        if (pool) {
                            state = WATER;
                        } else if (tunnel || chamber) {
                            state = AIR;
                        }
                    }
                    world.blocks[CaveWorld.index(x, y, z)] = state;
                }
            }
        }
    }

    private static void paint(CaveWorld world, CaveLayout layout, ImprovedNoise noise, RandomSource random) {
        for (int z = 0; z < SIZE_Z; z++) {
            for (int y = 0; y < SIZE_Y; y++) {
                for (int x = 0; x < SIZE_X; x++) {
                    int i = CaveWorld.index(x, y, z);
                    if (world.blocks[i] != STONE) {
                        continue;
                    }
                    double m = noise.noise(x * 0.07, y * 0.09, z * 0.07);
                    double deep = noise.noise(x * 0.05, 100.0, z * 0.05);
                    boolean deepslate = y < 13 + deep * 3.0;
                    BlockState state = deepslate ? Blocks.DEEPSLATE.defaultBlockState() : STONE;
                    if (m > 0.42) {
                        state = Blocks.TUFF.defaultBlockState();
                    } else if (m < -0.48) {
                        state = Blocks.ANDESITE.defaultBlockState();
                    }
                    float ore = random.nextFloat();
                    if (ore < 0.010F) {
                        state = deepslate ? Blocks.DEEPSLATE_COAL_ORE.defaultBlockState() : Blocks.COAL_ORE.defaultBlockState();
                    } else if (ore < 0.017F) {
                        state = deepslate ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState() : Blocks.IRON_ORE.defaultBlockState();
                    } else if (ore < 0.023F) {
                        state = deepslate ? Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState() : Blocks.COPPER_ORE.defaultBlockState();
                    } else if (ore < 0.027F) {
                        state = deepslate ? Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState() : Blocks.REDSTONE_ORE.defaultBlockState();
                    } else if (ore < 0.0285F) {
                        state = deepslate ? Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState() : Blocks.DIAMOND_ORE.defaultBlockState();
                    }
                    if (world.get(x, y + 1, z).isAir()) {
                        boolean chamber = chamberDistance(layout, x, y, z) < 1.2;
                        double floor = noise.noise(x * 0.15, 50.0, z * 0.15);
                        if (chamber && floor > -0.05) {
                            state = Blocks.MOSS_BLOCK.defaultBlockState();
                        } else if (!chamber && floor > 0.35) {
                            state = Blocks.GRAVEL.defaultBlockState();
                        }
                    }
                    world.blocks[i] = state;
                }
            }
        }
    }

    private static void decorate(CaveWorld world, CaveLayout layout, RandomSource random) {
        for (int z = 2; z < SIZE_Z - 2; z++) {
            float cx = CaveLayout.centerX(z + 0.5F);
            float r = CaveLayout.tunnelRadius(z + 0.5F);
            for (int y = 2; y < SIZE_Y - 2; y++) {
                for (int x = 2; x < SIZE_X - 2; x++) {
                    if (!world.get(x, y, z).isAir()) {
                        continue;
                    }
                    double e = chamberDistance(layout, x, y, z);
                    boolean chamber = e < 1.15;
                    boolean nearWall = chamber ? e > 0.55 : Math.abs(x + 0.5F - cx) > r * 0.45F;
                    BlockState below = world.get(x, y - 1, z);
                    BlockState above = world.get(x, y + 1, z);
                    if (below.canOcclude()) {
                        decorateFloor(world, x, y, z, below, chamber, nearWall, random);
                    } else if (above.canOcclude()) {
                        decorateCeiling(world, x, y, z, chamber, random);
                    }
                    if (world.get(x, y, z).isAir() && random.nextFloat() < (chamber ? 0.015F : 0.03F)) {
                        for (Direction side : Direction.Plane.HORIZONTAL) {
                            if (world.get(x + side.getStepX(), y, z + side.getStepZ()).canOcclude()) {
                                world.set(x, y, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(side), true));
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private static void decorateFloor(CaveWorld world, int x, int y, int z, BlockState below, boolean chamber, boolean nearWall,
                                      RandomSource random) {
        float roll = random.nextFloat();
        if (nearWall && random.nextFloat() < (chamber ? 0.012F : 0.03F)) {
            world.set(x, y, z, Blocks.TORCH.defaultBlockState());
        } else if (below.is(Blocks.MOSS_BLOCK) && roll < 0.35F) {
            world.set(x, y, z, Blocks.MOSS_CARPET.defaultBlockState());
        } else if (roll > 0.985F) {
            world.set(x, y - 1, z, Blocks.DRIPSTONE_BLOCK.defaultBlockState());
            world.set(x, y, z, dripstone(Direction.UP));
        } else if (chamber && nearWall && roll > 0.95F) {
            world.set(x, y - 1, z, Blocks.AMETHYST_BLOCK.defaultBlockState());
            world.set(x, y, z, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
        }
    }

    private static void decorateCeiling(CaveWorld world, int x, int y, int z, boolean chamber, RandomSource random) {
        float roll = random.nextFloat();
        if (chamber && roll < 0.016F) {
            world.set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        } else if (roll > 0.975F) {
            world.set(x, y, z, dripstone(Direction.DOWN));
        } else if (chamber && roll > 0.95F) {
            int length = 1 + random.nextInt(5);
            for (int i = 0; i < length; i++) {
                if (!world.get(x, y - i, z).isAir() || !world.get(x, y - i - 1, z).isAir()) {
                    break;
                }
                boolean tip = i == length - 1 || !world.get(x, y - i - 2, z).isAir();
                BlockState vine = tip ? Blocks.CAVE_VINES.defaultBlockState() : Blocks.CAVE_VINES_PLANT.defaultBlockState();
                world.set(x, y - i, z, vine.setValue(CaveVines.BERRIES, tip || random.nextFloat() < 0.3F));
                if (tip) {
                    break;
                }
            }
        }
    }

    private static BlockState dripstone(Direction direction) {
        return Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, direction)
                .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP);
    }

    /** Flood-fills block light from emitters through non-occluding blocks, like vanilla's block light. */
    private static void light(CaveWorld world) {
        int total = SIZE_X * SIZE_Y * SIZE_Z;
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        for (int i = 0; i < total; i++) {
            int emission = world.blocks[i].getLightEmission();
            if (emission > 0) {
                world.light[i] = (byte) emission;
                queue.enqueue(i);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.dequeueInt();
            int level = world.light[i];
            if (level <= 1) {
                continue;
            }
            int x = i % SIZE_X;
            int y = (i / SIZE_X) % SIZE_Y;
            int z = i / (SIZE_X * SIZE_Y);
            for (Direction direction : Direction.values()) {
                int nx = x + direction.getStepX();
                int ny = y + direction.getStepY();
                int nz = z + direction.getStepZ();
                if (!CaveWorld.inBounds(nx, ny, nz)) {
                    continue;
                }
                int n = CaveWorld.index(nx, ny, nz);
                if (world.light[n] < level - 1 && !CaveWorld.isOpaqueCube(world.blocks[n])) {
                    world.light[n] = (byte) (level - 1);
                    queue.enqueue(n);
                }
            }
        }
    }
}
