package com.lczme.glazuli.cave;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.joml.Vector3f;

/**
 * A lush grotto with a survival base grown into it, a single chamber with a corner per menu. A stream crosses the low
 * floor under a footbridge; behind it a stone terrace steps up to a timber lodge set into the back wall under the rock,
 * hung with glow berries (title). The player digs out a small diamond vein at the end of a branch tunnel
 * (singleplayer), friends sit round a campfire by the stream (multiplayer), a smith works in a lean-to (options), an
 * enchanting room opens off the terrace (mods) and a farm lies under a waterfall (everything else).
 */
final class MineScene {
    private static final int SX = 64;
    private static final int SY = 40;
    private static final int SZ = 60;
    /** Top of the low floor; the air starts one above. */
    private static final int FLOOR = 8;
    /** Top of the terrace the lodge stands on, and where its retaining wall faces the low floor. */
    private static final int TERRACE = 12;
    private static final int TERRACE_Z = 37;
    private static final float HALL_X = 32.0F;
    private static final float HALL_Z = 28.0F;
    private static final float HALL_RX = 27.0F;
    private static final float HALL_RZ = 26.0F;
    private static final float HALL_HEIGHT = 17.0F;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final CaveWorld world;
    private final RandomSource random;

    private MineScene(CaveWorld world, long seed) {
        this.world = world;
        this.random = RandomSource.create(seed);
    }

    static CaveWorld build(long seed) {
        SceneLook look = SceneLook.underground(0x07080A, 64.0F, 1.00F, 0.78F, 0.52F, 0.05F, 0.05F, 0.06F);
        CaveWorld world = new CaveWorld(SX, SY, SZ, null, false, null, look);
        new MineScene(world, seed).fill(seed);
        CaveGenerator.light(world);
        float[][] shots = {
            {33.0F, 12.6F, 21.0F, 32.0F, 17.0F, 44.0F},
            {7.4F, 10.6F, 16.4F, 3.7F, 10.1F, 15.4F},
            {40.5F, 11.5F, 25.0F, 46.5F, 9.8F, 31.5F},
            {45.5F, 11.0F, 13.0F, 55.0F, 9.8F, 13.0F},
            {46.0F, 14.6F, 38.5F, 46.0F, 13.6F, 49.0F},
            {25.0F, 12.5F, 22.0F, 11.0F, 10.0F, 29.5F},
        };
        Vector3f[] eyes = new Vector3f[shots.length];
        Vector3f[] looks = new Vector3f[shots.length];
        for (int i = 0; i < shots.length; i++) {
            eyes[i] = new Vector3f(shots[i][0], shots[i][1], shots[i][2]);
            looks[i] = new Vector3f(shots[i][3], shots[i][4], shots[i][5]);
        }
        CustomRig rig = new CustomRig(eyes, looks);
        rig.bind(world);
        world.rig = rig;
        return world;
    }

    // ---------------------------------------------------------------- helpers

    private void set(int x, int y, int z, BlockState state) {
        if (world.contains(x, y, z)) {
            world.set(x, y, z, state);
        }
    }

    private BlockState get(int x, int y, int z) {
        return world.contains(x, y, z) ? world.get(x, y, z) : Blocks.STONE.defaultBlockState();
    }

    private boolean air(int x, int y, int z) {
        return get(x, y, z).isAir();
    }

    private void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int z = z0; z <= z1; z++) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    private void carve(int x0, int y0, int z0, int x1, int y1, int z1) {
        box(x0, y0, z0, x1, y1, z1, AIR);
    }

    private void blockEntity(int x, int y, int z, String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        world.blockEntities.put(new BlockPos(x, y, z), tag);
    }

    /** The first air above the ground at a column: on the low floor or on the terrace; -1 if neither is open. */
    private int ground(int x, int z) {
        return air(x, FLOOR + 1, z) ? FLOOR + 1 : air(x, TERRACE + 1, z) ? TERRACE + 1 : -1;
    }

    /** The highest air going up from {@code y}, or -1 if {@code y} itself isn't air. */
    private int ceiling(int x, int y, int z) {
        if (y < 0 || !air(x, y, z)) {
            return -1;
        }
        while (y < SY - 1 && air(x, y + 1, z)) {
            y++;
        }
        return y;
    }

    private boolean bareRock(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.COARSE_DIRT);
    }

    private static BlockState log(Block block, Direction.Axis axis) {
        return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static BlockState stairs(Block block, Direction facing, boolean top) {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    private static BlockState slab(Block block, SlabType type) {
        return block.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static BlockState fence(boolean north, boolean east, boolean south, boolean west) {
        return Blocks.SPRUCE_FENCE.defaultBlockState().setValue(CrossCollisionBlock.NORTH, north)
                .setValue(CrossCollisionBlock.EAST, east).setValue(CrossCollisionBlock.SOUTH, south)
                .setValue(CrossCollisionBlock.WEST, west);
    }

    private static BlockState pane(Direction.Axis along) {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        return along == Direction.Axis.X
                ? pane.setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true)
                : pane.setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true);
    }

    private static BlockState hangingLantern() {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    private static BlockState facing(Block block, Direction direction) {
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction);
    }

    private static BlockState leaves() {
        return Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    private BlockState brick() {
        float r = random.nextFloat();
        return (r < 0.55F ? Blocks.STONE_BRICKS : r < 0.85F ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS).defaultBlockState();
    }

    private BlockState cobble() {
        return (random.nextFloat() < 0.6F ? Blocks.COBBLESTONE : Blocks.MOSSY_COBBLESTONE).defaultBlockState();
    }

    // ---------------------------------------------------------------- layout

    private void fill(long seed) {
        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed ^ 0x3A1EL));
        rock(noise);
        ores();
        hall(noise);
        terrace();
        railTunnel();
        branchMine();
        stream();
        waterfall();
        farm();
        lodge();
        enchantingRoom();
        camp();
        workshop();
        path();
        lanterns();
        greenery();
        glowBerries();
        dripstone();
    }

    /** Deepslate low down, tuff above it, then stone with patches of andesite: darker at the foot of every wall. */
    private void rock(ImprovedNoise noise) {
        for (int z = 0; z < SZ; z++) {
            for (int y = 0; y < SY; y++) {
                for (int x = 0; x < SX; x++) {
                    double n = noise.noise(x * 0.11, y * 0.11, z * 0.11);
                    boolean deep = y < FLOOR + 2 + n * 3.0 || x < 13 && z > 11 && z < 20 && y < FLOOR + 7;
                    Block rock = deep ? Blocks.DEEPSLATE
                            : y < FLOOR + 7 + n * 4.0 ? n < -0.25 ? Blocks.STONE : Blocks.TUFF
                            : n > 0.38 ? Blocks.ANDESITE : Blocks.STONE;
                    world.set(x, y, z, rock.defaultBlockState());
                }
            }
        }
    }

    private void ores() {
        Block[] kinds = {Blocks.COAL_ORE, Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE,
            Blocks.GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE};
        for (int i = 0; i < 380; i++) {
            int cx = random.nextInt(SX);
            int cy = 4 + random.nextInt(SY - 8);
            int cz = random.nextInt(SZ);
            Block kind = kinds[random.nextInt(kinds.length)];
            int size = 2 + random.nextInt(4);
            for (int j = 0; j < size; j++) {
                int x = cx + random.nextInt(3) - 1;
                int y = cy + random.nextInt(3) - 1;
                int z = cz + random.nextInt(3) - 1;
                if (world.contains(x, y, z) && !world.get(x, y, z).is(Blocks.TUFF)) {
                    world.set(x, y, z, deepslateVariant(kind, world.get(x, y, z)));
                }
            }
        }
    }

    private static BlockState deepslateVariant(Block ore, BlockState host) {
        if (!host.is(Blocks.DEEPSLATE)) {
            return ore.defaultBlockState();
        }
        Block deep = ore == Blocks.COAL_ORE ? Blocks.DEEPSLATE_COAL_ORE
                : ore == Blocks.IRON_ORE ? Blocks.DEEPSLATE_IRON_ORE
                : ore == Blocks.COPPER_ORE ? Blocks.DEEPSLATE_COPPER_ORE
                : ore == Blocks.GOLD_ORE ? Blocks.DEEPSLATE_GOLD_ORE
                : ore == Blocks.REDSTONE_ORE ? Blocks.DEEPSLATE_REDSTONE_ORE
                : ore == Blocks.LAPIS_ORE ? Blocks.DEEPSLATE_LAPIS_ORE : ore;
        return deep.defaultBlockState();
    }

    /** The grotto: a flat floor under a rough dome, scattered with gravel and coarse dirt. */
    private void hall(ImprovedNoise noise) {
        BlockState[] floor = {Blocks.GRAVEL.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState()};
        for (int z = 0; z < SZ; z++) {
            for (int x = 0; x < SX; x++) {
                float u = (x + 0.5F - HALL_X) / HALL_RX;
                float v = (z + 0.5F - HALL_Z) / HALL_RZ;
                double r2 = u * u + v * v + noise.noise(x * 0.09, 5.0, z * 0.09) * 0.12;
                if (r2 >= 1.0) {
                    continue;
                }
                int top = FLOOR + 1 + (int) (HALL_HEIGHT * Math.sqrt(1.0 - r2) + noise.noise(x * 0.2, 9.0, z * 0.2) * 2.0);
                for (int y = FLOOR + 1; y <= top; y++) {
                    set(x, y, z, AIR);
                }
                if (random.nextFloat() < 0.15F) {
                    set(x, FLOOR, z, floor[random.nextInt(floor.length)]);
                }
            }
        }
    }

    /**
     * The back of the grotto raised into a terrace: a retaining wall of mossy brick on a cobble footing with deepslate
     * piers standing proud of it, lanterns on top, vines hanging down; a moss-grown yard on top and steps up the middle.
     */
    private void terrace() {
        for (int z = TERRACE_Z; z < SZ; z++) {
            for (int x = 0; x < SX; x++) {
                if (!air(x, FLOOR + 1, z)) {
                    continue;
                }
                for (int y = FLOOR + 1; y <= TERRACE; y++) {
                    BlockState block = z == TERRACE_Z ? y <= FLOOR + 2 ? cobble() : brick() : Blocks.STONE.defaultBlockState();
                    set(x, y, z, block);
                }
                float r = random.nextFloat();
                BlockState top = r < 0.86F ? Blocks.MOSS_BLOCK.defaultBlockState()
                        : r < 0.95F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                set(x, TERRACE, z, z == TERRACE_Z ? brick() : top);
            }
        }
        for (int x : new int[]{24, 27, 37, 40}) {
            for (int y = FLOOR + 1; y <= TERRACE; y++) {
                set(x, y, TERRACE_Z - 1, y == TERRACE ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            }
            set(x, TERRACE + 1, TERRACE_Z - 1, Blocks.LANTERN.defaultBlockState());
        }
        BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true);
        for (int x = 4; x < SX - 4; x++) {
            if (x >= 28 && x <= 36 || random.nextFloat() > 0.3F || !air(x, TERRACE, TERRACE_Z - 1)) {
                continue;
            }
            int length = 1 + random.nextInt(3);
            for (int k = 0; k < length && air(x, TERRACE - k, TERRACE_Z - 1); k++) {
                set(x, TERRACE - k, TERRACE_Z - 1, vine);
            }
        }
        for (int i = 0; i < 4; i++) {
            int z = TERRACE_Z - 4 + i;
            int y = FLOOR + 1 + i;
            for (int x = 30; x <= 34; x++) {
                box(x, FLOOR + 1, z, x, y - 1, z, Blocks.STONE_BRICKS.defaultBlockState());
                set(x, y, z, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
            }
            box(29, FLOOR + 1, z, 29, y, z, brick());
            box(35, FLOOR + 1, z, 35, y, z, brick());
        }
        set(29, FLOOR + 2, TERRACE_Z - 4, Blocks.LANTERN.defaultBlockState());
        set(35, FLOOR + 2, TERRACE_Z - 4, Blocks.LANTERN.defaultBlockState());
        for (int z = TERRACE_Z; z <= 43; z++) {
            for (int x = 31; x <= 33; x++) {
                set(x, TERRACE, z, random.nextFloat() < 0.8F ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState());
            }
        }
        set(30, TERRACE + 1, TERRACE_Z + 1, Blocks.SPRUCE_FENCE.defaultBlockState());
        set(30, TERRACE + 2, TERRACE_Z + 1, Blocks.LANTERN.defaultBlockState());
        set(34, TERRACE + 1, TERRACE_Z + 1, Blocks.SPRUCE_FENCE.defaultBlockState());
        set(34, TERRACE + 2, TERRACE_Z + 1, Blocks.LANTERN.defaultBlockState());
    }

    /** The timbered rail tunnel in from the south, its rails ending at a chest and barrels inside the grotto. */
    private void railTunnel() {
        carve(30, FLOOR + 1, 0, 34, FLOOR + 4, 8);
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState beam = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X);
        for (int z : new int[]{1, 5, 8}) {
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                set(30, y, z, post);
                set(34, y, z, post);
            }
            for (int x = 30; x <= 34; x++) {
                set(x, FLOOR + 4, z, beam);
            }
            set(31, FLOOR + 3, z, hangingLantern());
        }
        for (int z = 0; z <= 15; z++) {
            set(32, FLOOR + 1, z, Blocks.RAIL.defaultBlockState());
        }
        set(30, FLOOR + 1, 14, Blocks.BARREL.defaultBlockState());
        set(30, FLOOR + 2, 14, Blocks.BARREL.defaultBlockState());
        set(31, FLOOR + 1, 15, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
        blockEntity(31, FLOOR + 1, 15, "minecraft:chest");
    }

    /**
     * A branch tunnel west into the deepslate, ending at a small diamond vein (three blocks, the way they turn up)
     * the player is digging out by torchlight.
     */
    private void branchMine() {
        carve(3, FLOOR + 1, 13, 12, FLOOR + 4, 17);
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState beam = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z);
        for (int x : new int[]{9, 11}) {
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                set(x, y, 13, post);
                set(x, y, 17, post);
            }
            for (int z = 13; z <= 17; z++) {
                set(x, FLOOR + 4, z, beam);
            }
        }
        for (int z = 12; z <= 18; z++) {
            for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
                BlockState face = random.nextFloat() < 0.12F ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState()
                        : random.nextFloat() < 0.1F ? Blocks.TUFF.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
                set(2, y, z, face);
            }
        }
        BlockState diamond = Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
        set(2, FLOOR + 1, 15, diamond);
        set(2, FLOOR + 2, 15, diamond);
        set(2, FLOOR + 2, 16, diamond);
        set(6, FLOOR + 3, 13, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        set(4, FLOOR + 3, 17, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
        set(7, FLOOR + 1, 17, Blocks.LANTERN.defaultBlockState());
        set(3, FLOOR + 1, 17, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(4, FLOOR + 1, 13, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(3, FLOOR + 1, 13, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(3, FLOOR + 2, 13, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        world.figures.add(new CaveWorld.Figure(3.7F, FLOOR + 1, 15.4F, 90.0F, true, 0,
                new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.TORCH), true));
    }

    /** A stream across the low floor, mossy banks with dripleaf and lily pads, and a humped footbridge for the path. */
    private void stream() {
        for (int x = 9; x < SX; x++) {
            for (int z = 26; z <= 27; z++) {
                if (air(x, FLOOR + 1, z)) {
                    set(x, FLOOR, z, Blocks.WATER.defaultBlockState());
                    set(x, FLOOR - 1, z, Blocks.WATER.defaultBlockState());
                    if (x % 6 == z % 2 * 3) {
                        set(x, FLOOR - 2, z, Blocks.SEA_LANTERN.defaultBlockState());
                    }
                    if (random.nextFloat() < 0.05F && (x < 29 || x > 35)) {
                        set(x, FLOOR + 1, z, Blocks.LILY_PAD.defaultBlockState());
                    }
                }
            }
            for (int z : new int[]{25, 28}) {
                if (!air(x, FLOOR + 1, z) || !bareRock(get(x, FLOOR, z)) || random.nextFloat() < 0.25F) {
                    continue;
                }
                set(x, FLOOR, z, random.nextFloat() < 0.8F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.CLAY.defaultBlockState());
                float r = random.nextFloat();
                if (x >= 29 && x <= 35) {
                    continue;
                }
                if (r < 0.08F) {
                    set(x, FLOOR + 1, z, facing(Blocks.BIG_DRIPLEAF, z == 25 ? Direction.SOUTH : Direction.NORTH));
                } else if (r < 0.3F) {
                    set(x, FLOOR + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
        set(31, FLOOR + 1, 25, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        set(32, FLOOR + 1, 25, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        set(33, FLOOR + 1, 25, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        set(31, FLOOR + 1, 28, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        set(32, FLOOR + 1, 28, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        set(33, FLOOR + 1, 28, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        box(31, FLOOR + 1, 26, 33, FLOOR + 1, 27, slab(Blocks.SPRUCE_SLAB, SlabType.TOP));
        for (int z = 25; z <= 28; z++) {
            set(30, FLOOR + 2, z, fence(z > 25, false, z < 28, false));
            set(34, FLOOR + 2, z, fence(z > 25, false, z < 28, false));
        }
        for (int z = 25; z <= 28; z++) {
            BlockState stringer = z == 25 || z == 28 ? Blocks.SPRUCE_LOG.defaultBlockState() : log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z);
            set(30, FLOOR + 1, z, stringer);
            set(34, FLOOR + 1, z, stringer);
        }
        set(30, FLOOR + 3, 25, Blocks.LANTERN.defaultBlockState());
        set(34, FLOOR + 3, 28, Blocks.LANTERN.defaultBlockState());
    }

    /** The stream's source: water falling from a crack in the west wall into a pool lit from below. */
    private void waterfall() {
        for (int z = 22; z <= 31; z++) {
            for (int x = 4; x <= 12; x++) {
                float u = (x + 0.5F - 8.5F) / 2.8F;
                float v = (z + 0.5F - 26.5F) / 3.0F;
                float r2 = u * u + v * v;
                if (r2 < 1.0F && air(x, FLOOR + 1, z) || x == 6 && (z == 26 || z == 27)) {
                    set(x, FLOOR + 1, z, AIR);
                    set(x, FLOOR, z, Blocks.WATER.defaultBlockState());
                    set(x, FLOOR - 1, z, Blocks.WATER.defaultBlockState());
                } else if (r2 < 1.6F && air(x, FLOOR + 1, z)) {
                    set(x, FLOOR, z, (random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE : Blocks.MOSS_BLOCK).defaultBlockState());
                }
            }
        }
        BlockState falling = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
        for (int z = 26; z <= 27; z++) {
            int top = Mth.clamp(ceiling(6, FLOOR + 1, z), FLOOR + 5, FLOOR + 9);
            for (int y = FLOOR + 1; y <= top; y++) {
                set(6, y, z, falling);
            }
            set(5, top, z, Blocks.WATER.defaultBlockState());
            set(5, top + 1, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            set(6, top + 1, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        }
        set(8, FLOOR - 1, 25, Blocks.SEA_LANTERN.defaultBlockState());
        set(9, FLOOR - 1, 28, Blocks.SEA_LANTERN.defaultBlockState());
        int bloom = ceiling(9, FLOOR + 1, 26);
        if (bloom > 0) {
            set(9, bloom, 26, Blocks.SPORE_BLOSSOM.defaultBlockState());
        }
    }

    /** Crops either side of a channel, a log border, lanterns on fence posts, hay and a composter. */
    private void farm() {
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        CropBlock[] rows = {wheat, (CropBlock) Blocks.CARROTS, null, (CropBlock) Blocks.POTATOES, (CropBlock) Blocks.BEETROOTS, wheat};
        BlockState soil = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
        for (int z = 30; z <= 35; z++) {
            CropBlock crop = rows[z - 30];
            for (int x = 11; x <= 21; x++) {
                set(x, FLOOR + 1, z, AIR);
                if (crop == null) {
                    set(x, FLOOR, z, Blocks.WATER.defaultBlockState());
                    continue;
                }
                set(x, FLOOR, z, soil);
                set(x, FLOOR + 1, z, crop.getStateForAge(Math.max(0, crop.getMaxAge() - random.nextInt(3))));
            }
        }
        BlockState alongX = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X);
        BlockState alongZ = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z);
        for (int x = 10; x <= 22; x++) {
            set(x, FLOOR, 29, alongX);
            set(x, FLOOR, 36, alongX);
            set(x, FLOOR + 1, 29, AIR);
            set(x, FLOOR + 1, 36, AIR);
        }
        for (int z = 30; z <= 35; z++) {
            set(10, FLOOR, z, alongZ);
            set(22, FLOOR, z, alongZ);
            set(10, FLOOR + 1, z, AIR);
            set(22, FLOOR + 1, z, AIR);
        }
        for (int[] corner : new int[][]{{10, 29}, {22, 29}, {10, 36}, {22, 36}}) {
            set(corner[0], FLOOR + 1, corner[1], Blocks.SPRUCE_FENCE.defaultBlockState());
            set(corner[0], FLOOR + 2, corner[1], Blocks.LANTERN.defaultBlockState());
        }
        set(23, FLOOR + 1, 31, Blocks.HAY_BLOCK.defaultBlockState());
        set(23, FLOOR + 1, 32, Blocks.HAY_BLOCK.defaultBlockState());
        set(23, FLOOR + 2, 32, Blocks.HAY_BLOCK.defaultBlockState());
        set(23, FLOOR + 1, 34, Blocks.COMPOSTER.defaultBlockState());
        set(16, FLOOR + 1, 34, AIR);
        world.figures.add(new CaveWorld.Figure(16.5F, FLOOR + 0.9375F, 34.5F, 160.0F, false, 5,
                new ItemStack(Items.IRON_HOE), ItemStack.EMPTY, true));
    }

    /**
     * The lodge, set into the back wall on the terrace. Dark oak posts stand a block proud of the facade and carry beams
     * at each floor; a cobble footing and stone-brick ground floor, spruce upper floor, panes set in the wall, lamps
     * inside. A balcony on posts over the door, a pent roof running back into the rock and a gabled dormer over the
     * balcony. Someone leans on the rail.
     */
    private void lodge() {
        int ground = TERRACE + 1;
        int upper = ground + 5;
        int eaves = upper + 4;
        for (int z = 40; z <= 44; z++) {
            int top = z <= 41 ? eaves : eaves + 1 + (z - 42);
            carve(24, ground, z, 40, top, z);
        }
        carve(26, ground, 45, 38, eaves, 50);
        carve(30, eaves + 1, 41, 34, eaves + 3, 46);
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        box(26, TERRACE, 45, 38, TERRACE, 50, planks);
        box(26, upper - 1, 45, 38, upper - 1, 50, planks);
        for (int x = 25; x <= 39; x++) {
            set(x, ground, 44, cobble());
            for (int y = ground + 1; y <= upper - 1; y++) {
                set(x, y, 44, y == upper - 1 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : stoneBricks);
            }
            for (int y = upper; y <= eaves + 2; y++) {
                set(x, y, 44, planks);
            }
            set(x, eaves + 1, 43, planks);
        }
        for (int x : new int[]{26, 27, 28, 36, 37, 38}) {
            set(x, ground + 1, 44, pane(Direction.Axis.X));
            set(x, ground + 2, 44, pane(Direction.Axis.X));
            set(x, upper + 1, 44, pane(Direction.Axis.X));
            set(x, upper + 2, 44, pane(Direction.Axis.X));
        }
        for (int x = 31; x <= 33; x++) {
            set(x, upper, 44, pane(Direction.Axis.X));
            set(x, upper + 1, 44, pane(Direction.Axis.X));
            set(x, upper + 2, 44, pane(Direction.Axis.X));
        }
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        set(32, ground, 44, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(32, ground + 1, 44, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        set(32, ground + 2, 44, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, true));
        BlockState post = log(Blocks.DARK_OAK_LOG, Direction.Axis.Y);
        BlockState beam = log(Blocks.DARK_OAK_LOG, Direction.Axis.X);
        for (int x = 25; x <= 39; x++) {
            boolean pier = x == 25 || x == 29 || x == 35 || x == 39;
            for (int y = ground; y <= eaves; y++) {
                if (pier) {
                    set(x, y, 43, post);
                } else if (y == upper - 1 || y == eaves) {
                    set(x, y, 43, beam);
                }
            }
        }
        for (int x = 24; x <= 40; x++) {
            if (x >= 29 && x <= 35) {
                continue;
            }
            for (int k = 0; k <= 5; k++) {
                int z = 42 + k;
                int y = eaves + 1 + k;
                if (k <= 2 || air(x, y, z)) {
                    set(x, y, z, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
                }
            }
            set(x, eaves, 42, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, true));
        }
        for (int z = 41; z <= 46; z++) {
            for (int k = 0; k <= 2; k++) {
                set(29 + k, eaves + 1 + k, z, stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST, false));
                set(35 - k, eaves + 1 + k, z, stairs(Blocks.DARK_OAK_STAIRS, Direction.WEST, false));
            }
            set(32, eaves + 3, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            set(32, eaves + 4, z, slab(Blocks.DARK_OAK_SLAB, SlabType.BOTTOM));
        }
        box(30, eaves + 1, 42, 34, eaves + 1, 42, planks);
        box(31, eaves + 2, 42, 33, eaves + 2, 42, planks);
        set(32, eaves + 2, 42, pane(Direction.Axis.X));
        set(30, eaves, 42, beam);
        set(31, eaves, 42, beam);
        set(32, eaves, 42, beam);
        set(33, eaves, 42, beam);
        set(34, eaves, 42, beam);
        box(29, upper - 1, 41, 35, upper - 1, 42, planks);
        for (int x = 29; x <= 35; x++) {
            set(x, upper, 41, fence(false, x < 35, x == 29 || x == 35, x > 29));
        }
        set(29, upper, 42, fence(true, false, true, false));
        set(35, upper, 42, fence(true, false, true, false));
        for (int y = ground; y < upper - 1; y++) {
            set(29, y, 41, Blocks.SPRUCE_FENCE.defaultBlockState());
            set(35, y, 41, Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        set(31, upper - 2, 41, hangingLantern());
        set(33, upper - 2, 41, hangingLantern());
        for (int x : new int[]{26, 27, 28, 36, 37, 38}) {
            set(x, ground, 43, Blocks.MOSS_BLOCK.defaultBlockState());
            Block flower = x % 3 == 0 ? Blocks.CORNFLOWER : x % 3 == 1 ? Blocks.AZURE_BLUET : Blocks.POPPY;
            set(x, ground + 1, 43, flower.defaultBlockState());
        }
        set(27, ground, 46, Blocks.BARREL.defaultBlockState());
        set(27, ground + 1, 46, Blocks.LANTERN.defaultBlockState());
        set(37, ground, 46, Blocks.BARREL.defaultBlockState());
        set(37, ground + 1, 46, Blocks.LANTERN.defaultBlockState());
        box(27, ground, 50, 31, ground + 2, 50, Blocks.BOOKSHELF.defaultBlockState());
        set(35, ground, 50, facing(Blocks.FURNACE, Direction.NORTH).setValue(BlockStateProperties.LIT, true));
        set(33, ground, 50, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        blockEntity(33, ground, 50, "minecraft:chest");
        set(27, upper, 46, Blocks.LANTERN.defaultBlockState());
        set(37, upper, 46, Blocks.LANTERN.defaultBlockState());
        set(32, upper, 47, Blocks.LANTERN.defaultBlockState());
        set(38, ground, 42, Blocks.BARREL.defaultBlockState());
        set(38, ground + 1, 42, Blocks.BARREL.defaultBlockState());
        set(39, ground, 41, Blocks.BARREL.defaultBlockState());
        set(26, ground, 41, Blocks.FLOWERING_AZALEA.defaultBlockState());
        set(25, ground, 40, leaves());
        set(24, ground, 41, leaves());
        set(24, ground + 1, 41, leaves());
        world.figures.add(new CaveWorld.Figure(32.5F, upper, 42.45F, 180.0F, false, 7, ItemStack.EMPTY, ItemStack.EMPTY, false));
    }

    /** An arched doorway in the rock east of the lodge: an enchanting table ringed by bookshelves, candles on top. */
    private void enchantingRoom() {
        int floor = TERRACE;
        carve(43, floor + 1, 45, 49, floor + 5, 52);
        for (int y = floor + 1; y <= floor + 5; y++) {
            for (int z = 45; z <= 52; z++) {
                set(43, y, z, brick());
                set(49, y, z, brick());
            }
            for (int x = 43; x <= 49; x++) {
                set(x, y, 52, brick());
                set(x, y, 45, brick());
            }
        }
        for (int z = 44; z <= 52; z++) {
            for (int x = 43; x <= 49; x++) {
                if (air(x, floor + 6, z)) {
                    set(x, floor + 6, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
                }
            }
        }
        carve(45, floor + 1, 45, 47, floor + 3, 45);
        set(45, floor + 3, 45, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
        set(47, floor + 3, 45, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
        set(44, floor + 4, 44, hangingLantern());
        set(48, floor + 4, 44, hangingLantern());
        set(44, floor + 5, 44, Blocks.CHAIN.defaultBlockState());
        set(48, floor + 5, 44, Blocks.CHAIN.defaultBlockState());
        box(44, floor, 46, 48, floor, 51, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        set(46, floor + 1, 49, Blocks.ENCHANTING_TABLE.defaultBlockState());
        blockEntity(46, floor + 1, 49, "minecraft:enchanting_table");
        for (int z = 47; z <= 51; z++) {
            box(44, floor + 1, z, 44, floor + 2, z, Blocks.BOOKSHELF.defaultBlockState());
            box(48, floor + 1, z, 48, floor + 2, z, Blocks.BOOKSHELF.defaultBlockState());
        }
        box(45, floor + 1, 51, 47, floor + 2, 51, Blocks.BOOKSHELF.defaultBlockState());
        for (int[] c : new int[][]{{44, 47}, {44, 50}, {48, 48}, {48, 51}, {46, 51}}) {
            set(c[0], floor + 3, c[1], Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(3))
                    .setValue(BlockStateProperties.LIT, true));
        }
        set(48, floor + 3, 49, Blocks.LANTERN.defaultBlockState());
        set(44, floor + 1, 46, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
        set(48, floor + 1, 46, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
    }

    /** By the stream east of the bridge: friends round a campfire, stair benches, supplies stacked behind. */
    private void camp() {
        int fx = 46;
        int fz = 31;
        set(fx, FLOOR + 1, fz, Blocks.CAMPFIRE.defaultBlockState());
        set(fx - 3, FLOOR + 1, fz, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        set(fx - 3, FLOOR + 1, fz + 1, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        set(fx + 3, FLOOR + 1, fz, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
        set(fx, FLOOR + 1, fz + 3, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        set(fx + 1, FLOOR + 1, fz + 3, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        set(51, FLOOR + 1, 34, Blocks.BARREL.defaultBlockState());
        set(52, FLOOR + 1, 34, Blocks.BARREL.defaultBlockState());
        set(51, FLOOR + 2, 34, Blocks.BARREL.defaultBlockState());
        set(52, FLOOR + 1, 33, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        blockEntity(52, FLOOR + 1, 33, "minecraft:chest");
        float[][] friends = {{43.6F, 29.8F}, {46.6F, 33.4F}, {48.8F, 29.6F}};
        ItemStack[] hands = {new ItemStack(Items.TORCH), new ItemStack(Items.BREAD), new ItemStack(Items.IRON_SHOVEL)};
        int[] skins = {2, 9, 14};
        for (int i = 0; i < friends.length; i++) {
            float yaw = (float) Math.toDegrees(Mth.atan2(-(fx + 0.5F - friends[i][0]), fz + 0.5F - friends[i][1]));
            world.figures.add(new CaveWorld.Figure(friends[i][0], FLOOR + 1, friends[i][1], yaw, false, skins[i], hands[i],
                    ItemStack.EMPTY, false));
        }
    }

    /** A lean-to against the east wall: furnaces, smithing table, anvil and grindstone, a smith at work. */
    private void workshop() {
        carve(50, FLOOR + 1, 8, 56, FLOOR + 4, 18);
        set(56, FLOOR + 1, 10, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(56, FLOOR + 1, 11, facing(Blocks.FURNACE, Direction.WEST).setValue(BlockStateProperties.LIT, true));
        set(56, FLOOR + 1, 12, facing(Blocks.BLAST_FURNACE, Direction.WEST).setValue(BlockStateProperties.LIT, true));
        set(56, FLOOR + 1, 13, facing(Blocks.SMOKER, Direction.WEST));
        set(56, FLOOR + 1, 14, Blocks.SMITHING_TABLE.defaultBlockState());
        set(56, FLOOR + 2, 11, Blocks.LANTERN.defaultBlockState());
        set(54, FLOOR + 1, 16, facing(Blocks.ANVIL, Direction.EAST));
        set(52, FLOOR + 1, 16, Blocks.GRINDSTONE.defaultBlockState());
        set(52, FLOOR + 1, 9, Blocks.BARREL.defaultBlockState());
        set(53, FLOOR + 1, 9, Blocks.BARREL.defaultBlockState());
        set(52, FLOOR + 2, 9, Blocks.BARREL.defaultBlockState());
        set(55, FLOOR + 1, 9, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        blockEntity(55, FLOOR + 1, 9, "minecraft:chest");
        BlockState post = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
        for (int z : new int[]{8, 18}) {
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                set(51, y, z, post);
            }
            set(52, FLOOR + 3, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, true));
        }
        box(51, FLOOR + 4, 8, 56, FLOOR + 4, 18, slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
        for (int z = 8; z <= 18; z++) {
            set(50, FLOOR + 4, z, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
        }
        set(54, FLOOR + 3, 13, hangingLantern());
        world.figures.add(new CaveWorld.Figure(54.5F, FLOOR + 1, 15.2F, 0.0F, false, 11,
                new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY, true));
    }

    /** A worn path from the end of the rails over the bridge to the steps. */
    private void path() {
        BlockState[] worn = {Blocks.DIRT_PATH.defaultBlockState(), Blocks.DIRT_PATH.defaultBlockState(),
            Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()};
        for (int z = 16; z <= TERRACE_Z - 5; z++) {
            if (z >= 25 && z <= 28) {
                continue;
            }
            for (int x = 31; x <= 33; x++) {
                if ((x == 32 || random.nextFloat() < 0.7F) && air(x, FLOOR + 1, z)) {
                    set(x, FLOOR, z, worn[random.nextInt(worn.length)]);
                }
            }
        }
        for (int[] p : new int[][]{{30, 19}, {34, 22}, {30, 31}}) {
            set(p[0], FLOOR + 1, p[1], Blocks.SPRUCE_FENCE.defaultBlockState());
            set(p[0], FLOOR + 2, p[1], Blocks.LANTERN.defaultBlockState());
        }
    }

    /** A few lanterns on chains over the low floor, high enough that the camera passes below them. */
    private void lanterns() {
        int[][] spots = {{22, 19}, {42, 19}, {16, 23}, {38, 31}};
        for (int[] spot : spots) {
            int x = spot[0];
            int z = spot[1];
            int top = ceiling(x, FLOOR + 1, z);
            int lantern = Math.max(FLOOR + 8, top - 3);
            if (top < 0 || lantern >= top) {
                continue;
            }
            for (int y = lantern + 1; y <= top; y++) {
                set(x, y, z, Blocks.CHAIN.defaultBlockState());
            }
            set(x, lantern, z, hangingLantern());
        }
    }

    /**
     * Moss over the low floor near water and over the terrace yard, azalea bushes and flowering leaves clinging to the
     * rock round the lodge.
     */
    private void greenery() {
        for (int z = 2; z < SZ - 2; z++) {
            for (int x = 2; x < SX - 2; x++) {
                int y = ground(x, z);
                if (y < 0 || !air(x, y, z)) {
                    continue;
                }
                BlockState below = get(x, y - 1, z);
                if (y == FLOOR + 1) {
                    float wet = Math.min(Math.abs(z - 26.5F) / 7.0F, Mth.sqrt((x - 8.0F) * (x - 8.0F) + (z - 27.0F) * (z - 27.0F)) / 9.0F);
                    if (bareRock(below) && random.nextFloat() > wet) {
                        set(x, y - 1, z, Blocks.MOSS_BLOCK.defaultBlockState());
                        below = Blocks.MOSS_BLOCK.defaultBlockState();
                    }
                }
                if (below.is(Blocks.MOSS_BLOCK) && random.nextFloat() < 0.22F && !(x >= 30 && x <= 34 && z < 44)) {
                    set(x, y, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
        int[][] bushes = {{24, 22}, {40, 21}, {14, 38}, {50, 38}, {13, 20}, {48, 22}};
        for (int[] b : bushes) {
            int y = ground(b[0], b[1]);
            if (y > 0) {
                set(b[0], y - 1, b[1], Blocks.MOSS_BLOCK.defaultBlockState());
                set(b[0], y, b[1], (random.nextBoolean() ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA).defaultBlockState());
            }
        }
        for (int i = 0; i < 260; i++) {
            int x = 18 + random.nextInt(34);
            int z = 38 + random.nextInt(12);
            int top = ceiling(x, TERRACE + 1, z);
            if (top < 0 || x >= 28 && x <= 36 && z <= 43) {
                continue;
            }
            for (int y = top; y > top - 2 && y > TERRACE + 6; y--) {
                if (air(x, y, z) && !air(x, y + 1, z) || air(x, y, z) && get(x, y + 1, z).is(Blocks.FLOWERING_AZALEA_LEAVES)) {
                    set(x, y, z, leaves());
                }
            }
        }
    }

    /** Glow berry vines: curtains over the lodge front, and hanging from the high parts of the dome. */
    private void glowBerries() {
        for (int x = 18; x <= 46; x++) {
            for (int z = 38; z <= 43; z++) {
                if (x >= 28 && x <= 36 || random.nextFloat() > 0.42F) {
                    continue;
                }
                hangVine(x, z, TERRACE + 6, 7);
            }
        }
        for (int i = 0; i < 140; i++) {
            hangVine(4 + random.nextInt(SX - 8), 3 + random.nextInt(SZ - 6), FLOOR + 8, 5);
        }
    }

    private void hangVine(int x, int z, int lowest, int maxLength) {
        int top = ceiling(x, Math.max(ground(x, z), 0), z);
        if (top < lowest + 1) {
            return;
        }
        int length = Math.min(top - lowest + 1, 1 + random.nextInt(maxLength));
        for (int k = 0; k < length; k++) {
            Block block = k == length - 1 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT;
            set(x, top - k, z, block.defaultBlockState().setValue(CaveVines.BERRIES, random.nextFloat() < 0.5F));
        }
    }

    /** Dripstone round the edges of the grotto: stalactites from the lower ceiling, stalagmites up from bare floor. */
    private void dripstone() {
        for (int i = 0; i < 90; i++) {
            int x = 4 + random.nextInt(SX - 8);
            int z = 3 + random.nextInt(SZ - 6);
            float u = (x + 0.5F - HALL_X) / HALL_RX;
            float v = (z + 0.5F - HALL_Z) / HALL_RZ;
            int floor = ground(x, z);
            if (u * u + v * v < 0.62F || floor < 0) {
                continue;
            }
            int top = ceiling(x, floor, z);
            int length = 1 + random.nextInt(3);
            if (random.nextBoolean()) {
                if (top - length < floor + 4 || !bareRock(get(x, top + 1, z))) {
                    continue;
                }
                set(x, top + 1, z, Blocks.DRIPSTONE_BLOCK.defaultBlockState());
                for (int k = 0; k < length; k++) {
                    set(x, top - k, z, dripstone(Direction.DOWN, length, k));
                }
            } else if (bareRock(get(x, floor - 1, z)) && top - floor > length + 2) {
                set(x, floor - 1, z, Blocks.DRIPSTONE_BLOCK.defaultBlockState());
                for (int k = 0; k < length; k++) {
                    set(x, floor + k, z, dripstone(Direction.UP, length, k));
                }
            }
        }
    }

    private static BlockState dripstone(Direction tip, int length, int k) {
        DripstoneThickness thickness = k == length - 1 ? DripstoneThickness.TIP
                : k == length - 2 ? DripstoneThickness.FRUSTUM : DripstoneThickness.BASE;
        return Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, tip)
                .setValue(PointedDripstoneBlock.THICKNESS, thickness);
    }
}
