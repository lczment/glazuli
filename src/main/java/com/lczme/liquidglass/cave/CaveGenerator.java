package com.lczme.liquidglass.cave;

import static com.lczme.liquidglass.cave.CaveWorld.SIZE_X;
import static com.lczme.liquidglass.cave.CaveWorld.SIZE_Y;
import static com.lczme.liquidglass.cave.CaveWorld.SIZE_Z;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.joml.Vector3f;

/** Carves, paints, decorates and lights a generated menu scene. Pure data, safe to run off the render thread. */
public final class CaveGenerator {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final Block[] CORALS = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK,
            Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK};
    private static final Block[] CORAL_PLANTS = {Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL,
            Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL};
    private static final Block[] CORAL_WALL_FANS = {Blocks.TUBE_CORAL_WALL_FAN, Blocks.BRAIN_CORAL_WALL_FAN,
            Blocks.BUBBLE_CORAL_WALL_FAN, Blocks.FIRE_CORAL_WALL_FAN, Blocks.HORN_CORAL_WALL_FAN};

    private CaveGenerator() {
    }

    public static CaveWorld generate(long seed, CaveTheme theme) {
        CaveLayout layout = new CaveLayout();
        CaveWorld world = new CaveWorld(SIZE_X, SIZE_Y, SIZE_Z, null, false, layout, theme.look());
        RandomSource random = RandomSource.create(seed + theme.ordinal());
        ImprovedNoise shape = new ImprovedNoise(random);
        ImprovedNoise paint = new ImprovedNoise(random);
        carve(world, layout, shape, theme);
        paint(world, layout, paint, random, theme);
        if (theme == CaveTheme.DEEP_DARK || theme == CaveTheme.OCEAN) {
            pillars(world, layout, random, theme);
        }
        decorate(world, layout, random, theme);
        lightIntroPath(world, theme);
        if (theme == CaveTheme.OCEAN) {
            flood(world);
        }
        light(world);
        return world;
    }

    /** The startup flight is revealed through the logo, so its tunnel needs dependable light, not just random torches. */
    private static void lightIntroPath(CaveWorld world, CaveTheme theme) {
        int side = 2;
        for (int z = (int) CaveLayout.TUNNEL_START_Z + 4; z < CaveLayout.CHAMBER_Z - CaveLayout.CHAMBER_RZ; z += 9) {
            side = -side;
            int x = Math.round(CaveLayout.centerX(z)) + side;
            int y = Math.round(CaveLayout.centerY(z));
            while (y < SIZE_Y - 1 && world.get(x, y + 1, z).isAir()) {
                y++;
            }
            if (!world.get(x, y, z).isAir() || world.get(x, y + 1, z).isAir()) {
                continue;
            }
            switch (theme) {
                case NETHER -> world.set(x, y + 1, z, Blocks.SHROOMLIGHT.defaultBlockState());
                case OCEAN -> world.set(x, y + 1, z, Blocks.SEA_LANTERN.defaultBlockState());
                case DEEP_DARK, ICE -> world.set(x, y, z, hanging(Blocks.SOUL_LANTERN));
                default -> world.set(x, y, z, hanging(Blocks.LANTERN));
            }
        }
    }

    private static BlockState hanging(Block lantern) {
        return lantern.defaultBlockState().setValue(LanternBlock.HANGING, true);
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

    private static void carve(CaveWorld world, CaveLayout layout, ImprovedNoise noise, CaveTheme theme) {
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
                            state = poolFluid(theme, y == layout.floorY - 1);
                        } else if (tunnel || chamber) {
                            state = AIR;
                        }
                    }
                    world.blocks[world.index(x, y, z)] = state;
                }
            }
        }
    }

    private static BlockState poolFluid(CaveTheme theme, boolean top) {
        return switch (theme) {
            case NETHER -> Blocks.LAVA.defaultBlockState();
            case ICE -> top ? Blocks.ICE.defaultBlockState() : WATER;
            default -> WATER;
        };
    }

    private static void paint(CaveWorld world, CaveLayout layout, ImprovedNoise noise, RandomSource random, CaveTheme theme) {
        for (int z = 0; z < SIZE_Z; z++) {
            for (int y = 0; y < SIZE_Y; y++) {
                for (int x = 0; x < SIZE_X; x++) {
                    int i = world.index(x, y, z);
                    if (world.blocks[i] != STONE) {
                        continue;
                    }
                    double m = noise.noise(x * 0.07, y * 0.09, z * 0.07);
                    double deep = noise.noise(x * 0.05, 100.0, z * 0.05);
                    boolean deepslate = y < 13 + deep * 3.0;
                    float ore = random.nextFloat();
                    BlockState state = switch (theme) {
                        case DEEP_DARK -> deepDarkRock(m, ore);
                        case ICE -> iceRock(m, deepslate);
                        case NETHER -> netherRock(m, ore);
                        default -> overworldRock(m, ore, deepslate, theme == CaveTheme.CAVE ? 1.0F : 0.5F);
                    };
                    boolean floor = world.get(x, y + 1, z).isAir();
                    boolean ceiling = !floor && world.get(x, y - 1, z).isAir();
                    boolean wall = !floor && !ceiling && touchesAirSideways(world, x, y, z);
                    if (floor || ceiling || wall) {
                        boolean chamber = chamberDistance(layout, x, y, z) < 1.2;
                        double f = noise.noise(x * 0.15, 50.0, z * 0.15);
                        double s = noise.noise(x * 0.2, y * 0.2 + 30.0, z * 0.2);
                        state = surface(theme, state, floor, ceiling, chamber, f, s, random);
                    }
                    world.blocks[i] = state;
                }
            }
        }
    }

    private static boolean touchesAirSideways(CaveWorld world, int x, int y, int z) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (world.get(x + side.getStepX(), y, z + side.getStepZ()).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static BlockState overworldRock(double m, float ore, boolean deepslate, float ores) {
        BlockState state = deepslate ? Blocks.DEEPSLATE.defaultBlockState() : STONE;
        if (m > 0.42) {
            state = Blocks.TUFF.defaultBlockState();
        } else if (m < -0.48) {
            state = Blocks.ANDESITE.defaultBlockState();
        }
        ore /= ores;
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
        return state;
    }

    private static BlockState deepDarkRock(double m, float ore) {
        if (ore < 0.004F) {
            return Blocks.DEEPSLATE_COAL_ORE.defaultBlockState();
        } else if (ore < 0.007F) {
            return Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
        } else if (ore < 0.0085F) {
            return Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
        }
        if (m > 0.42) {
            return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        }
        return m < -0.48 ? Blocks.TUFF.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
    }

    private static BlockState iceRock(double m, boolean deepslate) {
        if (m > 0.40) {
            return Blocks.BLUE_ICE.defaultBlockState();
        } else if (m < -0.45) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }
        return deepslate && m < -0.1 ? Blocks.CALCITE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
    }

    private static BlockState netherRock(double m, float ore) {
        if (ore < 0.012F) {
            return Blocks.NETHER_QUARTZ_ORE.defaultBlockState();
        } else if (ore < 0.020F) {
            return Blocks.NETHER_GOLD_ORE.defaultBlockState();
        } else if (ore < 0.0204F) {
            return Blocks.ANCIENT_DEBRIS.defaultBlockState();
        }
        if (m > 0.42) {
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        return m < -0.48 ? Blocks.BASALT.defaultBlockState() : Blocks.NETHERRACK.defaultBlockState();
    }

    /** The exposed face of the rock: floors ({@code f} noise), ceilings and walls ({@code s} noise). */
    private static BlockState surface(CaveTheme theme, BlockState rock, boolean floor, boolean ceiling, boolean chamber,
                                      double f, double s, RandomSource random) {
        switch (theme) {
            case CAVE -> {
                if (floor && chamber && f > -0.05) {
                    return Blocks.MOSS_BLOCK.defaultBlockState();
                } else if (floor && !chamber && f > 0.35) {
                    return Blocks.GRAVEL.defaultBlockState();
                }
            }
            case LUSH -> {
                if (floor) {
                    if (f > (chamber ? -0.35 : -0.1)) {
                        return Blocks.MOSS_BLOCK.defaultBlockState();
                    }
                    return f < -0.6 ? Blocks.CLAY.defaultBlockState() : rock;
                }
                if (ceiling) {
                    if (s > -0.1) {
                        return Blocks.MOSS_BLOCK.defaultBlockState();
                    }
                    return s < -0.45 ? Blocks.ROOTED_DIRT.defaultBlockState() : rock;
                }
                return s > 0.15 ? Blocks.MOSS_BLOCK.defaultBlockState() : rock;
            }
            case DEEP_DARK -> {
                if (s > (floor ? -0.5 : -0.2)) {
                    return Blocks.SCULK.defaultBlockState();
                }
            }
            case ICE -> {
                if (floor && f > -0.3) {
                    return Blocks.SNOW_BLOCK.defaultBlockState();
                }
            }
            case NETHER -> {
                if (floor) {
                    if (chamber && f > 0.05) {
                        return Blocks.CRIMSON_NYLIUM.defaultBlockState();
                    } else if (chamber && f < -0.25) {
                        return Blocks.WARPED_NYLIUM.defaultBlockState();
                    } else if (!chamber && f > 0.3) {
                        return Blocks.SOUL_SAND.defaultBlockState();
                    } else if (!chamber && f < -0.4) {
                        return Blocks.SOUL_SOIL.defaultBlockState();
                    }
                    return random.nextFloat() < 0.03F ? Blocks.MAGMA_BLOCK.defaultBlockState() : rock;
                }
                if (ceiling && s > 0.42) {
                    return Blocks.GLOWSTONE.defaultBlockState();
                }
            }
            case OCEAN -> {
                if (floor) {
                    if (f > -0.25) {
                        return Blocks.SAND.defaultBlockState();
                    }
                    return f < -0.55 ? Blocks.GRAVEL.defaultBlockState() : Blocks.CLAY.defaultBlockState();
                }
            }
        }
        return rock;
    }

    /** Ruined columns around the chamber: an ancient city's deepslate in the deep dark, prismarine under the sea. */
    private static void pillars(CaveWorld world, CaveLayout layout, RandomSource random, CaveTheme theme) {
        int count = 9;
        for (int i = 0; i < count; i++) {
            float angle = (float) (i * Math.PI * 2.0 / count + random.nextFloat() * 0.4);
            int x0 = Math.round(layout.chamberX + Mth.cos(angle) * CaveLayout.CHAMBER_RX * 0.78F);
            int z0 = Math.round(CaveLayout.CHAMBER_Z + Mth.sin(angle) * CaveLayout.CHAMBER_RZ * 0.78F);
            if (nearSpot(layout, x0, z0)) {
                continue;
            }
            int y0 = layout.floorY;
            while (y0 < SIZE_Y - 2 && !world.get(x0, y0, z0).isAir()) {
                y0++;
            }
            int height = 4 + random.nextInt(7);
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    int top = height - (random.nextFloat() < 0.4F ? random.nextInt(3) : 0);
                    for (int h = -1; h < top; h++) {
                        int y = y0 + h;
                        if (h >= 0 && !world.get(x0 + dx, y, z0 + dz).isAir()) {
                            break;
                        }
                        world.set(x0 + dx, y, z0 + dz, pillarBlock(theme, h, top, random));
                    }
                }
            }
            if (theme == CaveTheme.DEEP_DARK && world.get(x0, y0 + height, z0).isAir()) {
                world.set(x0, y0 + height, z0, Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }
    }

    private static boolean nearSpot(CaveLayout layout, int x, int z) {
        for (CaveCamera.Spot spot : CaveCamera.Spot.values()) {
            Vector3f eye = layout.spotEye(spot);
            float dx = x + 1.0F - eye.x;
            float dz = z + 1.0F - eye.z;
            if (dx * dx + dz * dz < 16.0F) {
                return true;
            }
        }
        return false;
    }

    private static BlockState pillarBlock(CaveTheme theme, int h, int top, RandomSource random) {
        float roll = random.nextFloat();
        if (theme == CaveTheme.OCEAN) {
            if (h == top - 1 && roll < 0.5F) {
                return Blocks.SEA_LANTERN.defaultBlockState();
            }
            if (h < 0 || roll < 0.2F) {
                return Blocks.DARK_PRISMARINE.defaultBlockState();
            }
            return roll < 0.6F ? Blocks.PRISMARINE_BRICKS.defaultBlockState() : Blocks.PRISMARINE.defaultBlockState();
        }
        if (h < 0) {
            return Blocks.REINFORCED_DEEPSLATE.defaultBlockState();
        }
        if (h == top - 1) {
            return Blocks.CHISELED_DEEPSLATE.defaultBlockState();
        }
        if (roll < 0.3F) {
            return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        }
        return roll < 0.75F ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    private static void decorate(CaveWorld world, CaveLayout layout, RandomSource random, CaveTheme theme) {
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
                        switch (theme) {
                            case CAVE -> floorCave(world, x, y, z, below, chamber, nearWall, random);
                            case LUSH -> floorLush(world, x, y, z, below, nearWall, random);
                            case DEEP_DARK -> floorDeepDark(world, x, y, z, below, random);
                            case ICE -> floorIce(world, x, y, z, below, chamber, nearWall, random);
                            case NETHER -> floorNether(world, x, y, z, below, random);
                            case OCEAN -> floorOcean(world, x, y, z, below, chamber, random);
                        }
                    } else if (above.canOcclude()) {
                        switch (theme) {
                            case CAVE -> ceilingCave(world, x, y, z, chamber, random);
                            case LUSH -> ceilingLush(world, x, y, z, above, chamber, random);
                            case DEEP_DARK -> ceilingLantern(world, x, y, z, chamber ? 0.016F : 0.008F, random);
                            case ICE -> ceilingIce(world, x, y, z, chamber, random);
                            case NETHER -> ceilingNether(world, x, y, z, chamber, random);
                            case OCEAN -> {
                            }
                        }
                    }
                    if (world.get(x, y, z).isAir()) {
                        decorateWall(world, x, y, z, chamber, random, theme);
                    }
                }
            }
        }
    }

    private static void decorateWall(CaveWorld world, int x, int y, int z, boolean chamber, RandomSource random, CaveTheme theme) {
        float chance = switch (theme) {
            case CAVE -> chamber ? 0.015F : 0.03F;
            case DEEP_DARK -> 0.07F;
            case NETHER -> 0.0F;
            case OCEAN -> chamber ? 0.03F : 0.012F;
            default -> 0.012F;
        };
        if (random.nextFloat() >= chance) {
            return;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int wx = x + side.getStepX();
            int wz = z + side.getStepZ();
            BlockState wall = world.get(wx, y, wz);
            if (!wall.canOcclude()) {
                continue;
            }
            if ((theme == CaveTheme.ICE || theme == CaveTheme.OCEAN) && random.nextFloat() < 0.3F && CaveWorld.isOpaqueCube(wall)) {
                world.set(wx, y, wz, Blocks.SEA_LANTERN.defaultBlockState());
            } else if (theme == CaveTheme.OCEAN && chamber) {
                Block fan = CORAL_WALL_FANS[random.nextInt(CORAL_WALL_FANS.length)];
                world.set(x, y, z, fan.defaultBlockState().setValue(BaseCoralWallFanBlock.FACING, side.getOpposite()));
            } else {
                Block lichen = theme == CaveTheme.DEEP_DARK ? Blocks.SCULK_VEIN : Blocks.GLOW_LICHEN;
                world.set(x, y, z, lichen.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(side), true));
            }
            return;
        }
    }

    private static void floorCave(CaveWorld world, int x, int y, int z, BlockState below, boolean chamber, boolean nearWall,
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

    private static void floorLush(CaveWorld world, int x, int y, int z, BlockState below, boolean nearWall, RandomSource random) {
        float roll = random.nextFloat();
        if (nearWall && roll < 0.004F) {
            world.set(x, y, z, Blocks.TORCH.defaultBlockState());
            return;
        }
        if (!below.is(Blocks.MOSS_BLOCK)) {
            return;
        }
        if (roll < 0.22F) {
            world.set(x, y, z, Blocks.MOSS_CARPET.defaultBlockState());
        } else if (roll < 0.32F) {
            world.set(x, y, z, Blocks.SHORT_GRASS.defaultBlockState());
        } else if (roll < 0.35F) {
            world.set(x, y, z, Blocks.AZALEA.defaultBlockState());
        } else if (roll < 0.37F) {
            world.set(x, y, z, Blocks.FLOWERING_AZALEA.defaultBlockState());
        } else if (roll < 0.39F) {
            world.set(x, y, z, Blocks.BIG_DRIPLEAF.defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
        }
    }

    private static void floorDeepDark(CaveWorld world, int x, int y, int z, BlockState below, RandomSource random) {
        if (!below.is(Blocks.SCULK)) {
            return;
        }
        float roll = random.nextFloat();
        if (roll < 0.012F) {
            world.set(x, y, z, Blocks.SCULK_SENSOR.defaultBlockState());
        } else if (roll < 0.016F) {
            world.set(x, y, z, Blocks.SCULK_SHRIEKER.defaultBlockState());
        } else if (roll < 0.019F) {
            world.set(x, y, z, Blocks.SCULK_CATALYST.defaultBlockState());
        } else if (roll < 0.027F) {
            world.set(x, y, z, Blocks.CANDLE.defaultBlockState()
                    .setValue(CandleBlock.CANDLES, 1 + random.nextInt(4)).setValue(CandleBlock.LIT, true));
        }
    }

    private static void floorIce(CaveWorld world, int x, int y, int z, BlockState below, boolean chamber, boolean nearWall,
                                 RandomSource random) {
        float roll = random.nextFloat();
        if (nearWall && roll < (chamber ? 0.006F : 0.012F)) {
            world.set(x, y, z, Blocks.LANTERN.defaultBlockState());
        } else if (below.is(Blocks.SNOW_BLOCK) && roll < 0.35F) {
            world.set(x, y, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + random.nextInt(2)));
        } else if (roll > 0.982F) {
            spike(world, x, y, z, 1, 1 + random.nextInt(4));
        }
    }

    private static void floorNether(CaveWorld world, int x, int y, int z, BlockState below, RandomSource random) {
        float roll = random.nextFloat();
        if (below.is(Blocks.CRIMSON_NYLIUM)) {
            if (roll < 0.18F) {
                world.set(x, y, z, Blocks.CRIMSON_ROOTS.defaultBlockState());
            } else if (roll < 0.24F) {
                world.set(x, y, z, Blocks.CRIMSON_FUNGUS.defaultBlockState());
            }
        } else if (below.is(Blocks.WARPED_NYLIUM)) {
            if (roll < 0.15F) {
                world.set(x, y, z, Blocks.WARPED_ROOTS.defaultBlockState());
            } else if (roll < 0.22F) {
                world.set(x, y, z, Blocks.NETHER_SPROUTS.defaultBlockState());
            } else if (roll < 0.27F) {
                world.set(x, y, z, Blocks.WARPED_FUNGUS.defaultBlockState());
            }
        } else if (below.is(Blocks.SOUL_SAND) || below.is(Blocks.SOUL_SOIL)) {
            if (roll < 0.04F) {
                world.set(x, y, z, Blocks.SOUL_FIRE.defaultBlockState());
            }
        } else if (below.is(Blocks.NETHERRACK) && roll < 0.012F) {
            world.set(x, y, z, Blocks.FIRE.defaultBlockState());
        }
    }

    private static void floorOcean(CaveWorld world, int x, int y, int z, BlockState below, boolean chamber, RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.25F) {
            world.set(x, y, z, Blocks.SEAGRASS.defaultBlockState());
        } else if (roll < 0.30F && world.get(x, y + 1, z).isAir()) {
            world.set(x, y, z, Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.LOWER));
            world.set(x, y + 1, z, Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER));
        } else if (roll < 0.36F) {
            int length = 2 + random.nextInt(9);
            int top = y;
            while (top - y < length - 1 && world.get(x, top + 1, z).isAir() && world.get(x, top + 2, z).isAir()) {
                world.set(x, top, z, Blocks.KELP_PLANT.defaultBlockState());
                top++;
            }
            world.set(x, top, z, Blocks.KELP.defaultBlockState());
        } else if (roll < 0.39F) {
            world.set(x, y, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + random.nextInt(4)));
        } else if (chamber && roll < 0.47F && CaveWorld.isOpaqueCube(below)) {
            int kind = random.nextInt(CORALS.length);
            world.set(x, y - 1, z, CORALS[kind].defaultBlockState());
            world.set(x, y, z, CORAL_PLANTS[random.nextInt(CORAL_PLANTS.length)].defaultBlockState());
        }
    }

    private static void ceilingCave(CaveWorld world, int x, int y, int z, boolean chamber, RandomSource random) {
        float roll = random.nextFloat();
        if (chamber && roll < 0.016F) {
            world.set(x, y, z, hanging(Blocks.LANTERN));
        } else if (roll > 0.975F) {
            world.set(x, y, z, dripstone(Direction.DOWN));
        } else if (chamber && roll > 0.95F) {
            vines(world, x, y, z, 1 + random.nextInt(5), 0.3F, random);
        }
    }

    private static void ceilingLush(CaveWorld world, int x, int y, int z, BlockState above, boolean chamber, RandomSource random) {
        float roll = random.nextFloat();
        if (chamber && roll < 0.006F) {
            world.set(x, y, z, hanging(Blocks.LANTERN));
        } else if (roll < (chamber ? 0.10F : 0.05F)) {
            vines(world, x, y, z, 1 + random.nextInt(7), 0.45F, random);
        } else if (chamber && roll < 0.112F) {
            world.set(x, y, z, Blocks.SPORE_BLOSSOM.defaultBlockState());
        } else if (roll < 0.17F && (above.is(Blocks.ROOTED_DIRT) || above.is(Blocks.MOSS_BLOCK))) {
            world.set(x, y, z, Blocks.HANGING_ROOTS.defaultBlockState());
        } else if (roll > 0.985F) {
            world.set(x, y, z, dripstone(Direction.DOWN));
        }
    }

    private static void ceilingLantern(CaveWorld world, int x, int y, int z, float chance, RandomSource random) {
        if (random.nextFloat() < chance) {
            world.set(x, y, z, hanging(Blocks.SOUL_LANTERN));
        }
    }

    private static void ceilingIce(CaveWorld world, int x, int y, int z, boolean chamber, RandomSource random) {
        float roll = random.nextFloat();
        if (roll < (chamber ? 0.014F : 0.01F)) {
            world.set(x, y, z, hanging(Blocks.SOUL_LANTERN));
        } else if (roll > 0.96F) {
            spike(world, x, y, z, -1, 1 + random.nextInt(3));
        }
    }

    private static void ceilingNether(CaveWorld world, int x, int y, int z, boolean chamber, RandomSource random) {
        float roll = random.nextFloat();
        if (roll < (chamber ? 0.06F : 0.025F)) {
            int length = 1 + random.nextInt(6);
            for (int i = 0; i < length; i++) {
                if (!world.get(x, y - i, z).isAir() || !world.get(x, y - i - 1, z).isAir()) {
                    break;
                }
                boolean tip = i == length - 1 || !world.get(x, y - i - 2, z).isAir();
                world.set(x, y - i, z, tip ? Blocks.WEEPING_VINES.defaultBlockState() : Blocks.WEEPING_VINES_PLANT.defaultBlockState());
                if (tip) {
                    break;
                }
            }
        } else if (roll > 0.995F) {
            world.set(x, y + 1, z, Blocks.SHROOMLIGHT.defaultBlockState());
        }
    }

    /** Ice spike growing up ({@code step} 1) from the floor or down from the ceiling. */
    private static void spike(CaveWorld world, int x, int y, int z, int step, int length) {
        for (int i = 0; i < length; i++) {
            int yy = y + i * step;
            if (!world.get(x, yy, z).isAir()) {
                break;
            }
            world.set(x, yy, z, i == 0 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
        }
    }

    private static void vines(CaveWorld world, int x, int y, int z, int length, float berries, RandomSource random) {
        for (int i = 0; i < length; i++) {
            if (!world.get(x, y - i, z).isAir() || !world.get(x, y - i - 1, z).isAir()) {
                break;
            }
            boolean tip = i == length - 1 || !world.get(x, y - i - 2, z).isAir();
            BlockState vine = tip ? Blocks.CAVE_VINES.defaultBlockState() : Blocks.CAVE_VINES_PLANT.defaultBlockState();
            world.set(x, y - i, z, vine.setValue(CaveVines.BERRIES, tip || random.nextFloat() < berries));
            if (tip) {
                break;
            }
        }
    }

    private static BlockState dripstone(Direction direction) {
        return Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, direction)
                .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP);
    }

    /** Sunken: every open cell becomes water, everything that can be waterlogged is. */
    private static void flood(CaveWorld world) {
        for (int i = 0; i < world.blocks.length; i++) {
            BlockState state = world.blocks[i];
            if (state.isAir()) {
                world.blocks[i] = WATER;
            } else if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                world.blocks[i] = state.setValue(BlockStateProperties.WATERLOGGED, true);
            }
        }
    }

    /** Flood-fills block light from emitters through non-occluding blocks, like vanilla's block light. */
    static void light(CaveWorld world) {
        int sx = world.sizeX;
        int sy = world.sizeY;
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        for (int i = 0; i < world.blocks.length; i++) {
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
            int x = i % sx;
            int y = (i / sx) % sy;
            int z = i / (sx * sy);
            for (Direction direction : Direction.values()) {
                int nx = x + direction.getStepX();
                int ny = y + direction.getStepY();
                int nz = z + direction.getStepZ();
                if (!world.contains(nx, ny, nz)) {
                    continue;
                }
                int n = world.index(nx, ny, nz);
                if (world.light[n] < level - 1 && !CaveWorld.isOpaqueCube(world.blocks[n])) {
                    world.light[n] = (byte) (level - 1);
                    queue.enqueue(n);
                }
            }
        }
    }
}
