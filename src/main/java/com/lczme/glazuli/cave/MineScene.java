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
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.joml.Vector3f;

/**
 * A tall lush cavern seen from high up (title): warm lights set into the rock, timber bridges spanning the shaft hung
 * with glow berries, glass strands falling to a pond, cherry trees, and at the bottom a cottage with a farm and lit
 * stone paths. Round the floor: the player digging out a diamond vein in a side tunnel (singleplayer), friends at a
 * campfire (multiplayer), a workshop dug into the wall (options), an enchanting room behind an amethyst arch (mods)
 * and the farm by the pond (everything else).
 */
final class MineScene {
    private static final int SX = 48;
    private static final int SY = 56;
    private static final int SZ = 48;
    /** Top of the floor; the air starts one above. */
    private static final int FLOOR = 6;
    private static final float CX = 24.0F;
    private static final float CZ = 24.0F;
    private static final float RX = 17.0F;
    private static final float RZ = 16.0F;
    /** Where the shaft starts closing into its roof, and how far up the roof goes. */
    private static final int DOME = 44;
    private static final int ROOF = 9;
    private static final float POND_X = 29.0F;
    private static final float POND_Z = 23.0F;
    private static final float POND_R = 6.0F;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final CaveWorld world;
    private final RandomSource random;

    private MineScene(CaveWorld world, long seed) {
        this.world = world;
        this.random = RandomSource.create(seed);
    }

    static CaveWorld build(long seed) {
        SceneLook look = SceneLook.underground(0x07080A, 72.0F, 1.00F, 0.80F, 0.55F, 0.05F, 0.05F, 0.06F);
        CaveWorld world = new CaveWorld(SX, SY, SZ, null, false, null, look);
        new MineScene(world, seed).fill(seed);
        CaveGenerator.light(world);
        float[][] shots = {
            {34.0F, 32.0F, 32.0F, 14.0F, 8.0F, 20.0F},
            {31.4F, 8.6F, 6.4F, 30.5F, 8.1F, 2.7F},
            {32.5F, 9.5F, 20.0F, 38.5F, 7.8F, 25.5F},
            {16.0F, 9.0F, 15.0F, 6.0F, 7.8F, 14.5F},
            {19.5F, 9.0F, 14.0F, 19.0F, 7.8F, 4.0F},
            {27.0F, 10.0F, 36.0F, 14.0F, 7.5F, 27.0F},
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

    /** The highest air going up from {@code y}, or -1 if {@code y} itself isn't air. */
    private int ceiling(int x, int y, int z) {
        if (!air(x, y, z)) {
            return -1;
        }
        while (y < SY - 1 && air(x, y + 1, z)) {
            y++;
        }
        return y;
    }

    private static boolean rock(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.GRAVEL);
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

    private static BlockState leaves(Block block) {
        return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    // ---------------------------------------------------------------- layout

    private void fill(long seed) {
        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed ^ 0x3A1EL));
        stone(noise);
        shaft(noise);
        floor(noise);
        pond();
        cottage();
        farm();
        workshop();
        enchantingRoom();
        mineTunnel();
        camp();
        paths();
        cherry(16, 36, 6, 3.6F);
        cherry(31, 32, 9, 4.2F);
        bridges();
        amethyst(-0.75F, -0.45F, 22);
        wallLights();
        wallGreenery();
        ceilingVines();
    }

    /** Stone with patches of andesite, tuff and cobble, going to deepslate at the foot of the walls. */
    private void stone(ImprovedNoise noise) {
        for (int z = 0; z < SZ; z++) {
            for (int y = 0; y < SY; y++) {
                for (int x = 0; x < SX; x++) {
                    double n = noise.noise(x * 0.13, y * 0.13, z * 0.13);
                    double m = noise.noise(x * 0.31 + 40.0, y * 0.31, z * 0.31);
                    Block block = y < FLOOR + 2 + n * 3.0 ? m > 0.25 ? Blocks.COBBLED_DEEPSLATE : Blocks.DEEPSLATE
                            : n > 0.33 ? Blocks.ANDESITE : n < -0.33 ? Blocks.TUFF : m > 0.3 ? Blocks.COBBLESTONE : Blocks.STONE;
                    world.set(x, y, z, block.defaultBlockState());
                }
            }
        }
    }

    /** The shaft: a rough oval well, bulging and narrowing with height, closing into a domed roof. */
    private void shaft(ImprovedNoise noise) {
        for (int y = FLOOR + 1; y < SY - 1; y++) {
            float closing = y < DOME ? 1.0F : 1.0F - (float) (y - DOME) * (y - DOME) / (ROOF * ROOF);
            if (closing <= 0.0F) {
                break;
            }
            float scale = Mth.sqrt(closing) * (1.0F + 0.06F * Mth.sin(y * 0.21F));
            for (int z = 0; z < SZ; z++) {
                for (int x = 0; x < SX; x++) {
                    float u = (x + 0.5F - CX) / (RX * scale);
                    float v = (z + 0.5F - CZ) / (RZ * scale);
                    double r2 = u * u + v * v + noise.noise(x * 0.1, y * 0.1, z * 0.1) * 0.22;
                    if (r2 < 1.0) {
                        world.set(x, y, z, AIR);
                    }
                }
            }
        }
    }

    /** Moss over most of the floor, gravel and coarse dirt where it wears through, rooted moss near the walls. */
    private void floor(ImprovedNoise noise) {
        for (int z = 0; z < SZ; z++) {
            for (int x = 0; x < SX; x++) {
                if (!air(x, FLOOR + 1, z)) {
                    continue;
                }
                double n = noise.noise(x * 0.17, 3.0, z * 0.17);
                Block block = n > 0.32 ? Blocks.GRAVEL : n < -0.4 ? Blocks.COARSE_DIRT : Blocks.MOSS_BLOCK;
                set(x, FLOOR, z, block.defaultBlockState());
                if (block == Blocks.MOSS_BLOCK && random.nextFloat() < 0.18F) {
                    set(x, FLOOR + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
    }

    /** The pond: three deep, a stony rim, seagrass and kelp, sea pickles glowing on the bottom, lily pads. */
    private void pond() {
        for (int z = 0; z < SZ; z++) {
            for (int x = 0; x < SX; x++) {
                float d = Mth.sqrt((x + 0.5F - POND_X) * (x + 0.5F - POND_X) + (z + 0.5F - POND_Z) * (z + 0.5F - POND_Z));
                float edge = POND_R + Mth.sin(x * 0.7F + z * 0.4F) * 0.8F;
                if (d < edge) {
                    int depth = d < edge - 2.0F ? 3 : d < edge - 1.0F ? 2 : 1;
                    set(x, FLOOR + 1, z, AIR);
                    for (int k = 0; k < depth; k++) {
                        set(x, FLOOR - k, z, Blocks.WATER.defaultBlockState());
                    }
                    set(x, FLOOR - depth, z, (random.nextFloat() < 0.6F ? Blocks.CLAY : Blocks.GRAVEL).defaultBlockState());
                    float r = random.nextFloat();
                    int bottom = FLOOR - depth + 1;
                    if (depth >= 2 && r < 0.12F) {
                        set(x, bottom, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + random.nextInt(4)));
                    } else if (depth == 3 && r < 0.3F) {
                        set(x, bottom, z, Blocks.KELP_PLANT.defaultBlockState());
                        set(x, bottom + 1, z, Blocks.KELP.defaultBlockState());
                    } else if (r < 0.55F) {
                        set(x, bottom, z, Blocks.SEAGRASS.defaultBlockState());
                    }
                    if (random.nextFloat() < 0.06F) {
                        set(x, FLOOR + 1, z, Blocks.LILY_PAD.defaultBlockState());
                    }
                } else if (d < edge + 1.3F && air(x, FLOOR + 1, z)) {
                    float r = random.nextFloat();
                    set(x, FLOOR, z, (r < 0.4F ? Blocks.MOSSY_COBBLESTONE : r < 0.7F ? Blocks.STONE : Blocks.MOSS_BLOCK).defaultBlockState());
                    if (air(x, FLOOR + 1, z) && random.nextFloat() < 0.15F) {
                        set(x, FLOOR + 1, z, Blocks.FERN.defaultBlockState());
                    } else if (get(x, FLOOR + 1, z).is(Blocks.MOSS_CARPET)) {
                        set(x, FLOOR + 1, z, AIR);
                    }
                }
            }
        }
    }

    /**
     * A cottage against the west wall facing the pond: cobble footing, spruce walls framed in stripped logs, lit
     * windows, a stone-brick gable roof with an overhang, a cobble chimney and lanterns under the eaves.
     */
    private void cottage() {
        int x0 = 8;
        int x1 = 14;
        int z0 = 18;
        int z1 = 26;
        int ridge = 11;
        carve(x0 - 1, FLOOR + 1, z0 - 1, x1 + 2, FLOOR + 11, z1 + 1);
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState post = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
        box(x0, FLOOR, z0, x1, FLOOR, z1, Blocks.COBBLESTONE.defaultBlockState());
        box(x0 + 1, FLOOR, z0 + 1, x1 - 1, FLOOR, z1 - 1, planks);
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                if (x != x0 && x != x1 && z != z0 && z != z1) {
                    continue;
                }
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                set(x, FLOOR + 1, z, random.nextFloat() < 0.3F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                for (int y = FLOOR + 2; y <= FLOOR + 4; y++) {
                    set(x, y, z, corner || z == 22 && x == x1 ? post : planks);
                }
            }
        }
        for (int z : new int[]{19, 20, 24, 25}) {
            set(x1, FLOOR + 2, z, pane(Direction.Axis.Z));
            set(x1, FLOOR + 3, z, pane(Direction.Axis.Z));
            set(x0, FLOOR + 3, z, pane(Direction.Axis.Z));
        }
        set(11, FLOOR + 3, z1, pane(Direction.Axis.X));
        set(11, FLOOR + 3, z0, pane(Direction.Axis.X));
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST);
        set(x1, FLOOR + 1, 22, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(x1, FLOOR + 2, 22, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        set(x1, FLOOR + 3, 22, post);
        int eaves = FLOOR + 5;
        for (int x = x0; x <= x1; x++) {
            set(x, eaves, z0, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
            set(x, eaves, z1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
        }
        for (int z = z0; z <= z1; z++) {
            set(x0, eaves, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
            set(x1, eaves, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
        }
        for (int k = 1; k <= 3; k++) {
            for (int x = x0 + k; x <= x1 - k; x++) {
                set(x, eaves + k, z0, planks);
                set(x, eaves + k, z1, planks);
            }
        }
        set(ridge, eaves + 2, z0, pane(Direction.Axis.X));
        set(ridge, eaves + 2, z1, pane(Direction.Axis.X));
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            for (int k = 0; k <= 3; k++) {
                set(x0 - 1 + k, eaves + k, z, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, false));
                set(x1 + 1 - k, eaves + k, z, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, false));
            }
            set(ridge, eaves + 4, z, Blocks.STONE_BRICKS.defaultBlockState());
            set(ridge, eaves + 5, z, slab(Blocks.STONE_BRICK_SLAB, SlabType.BOTTOM));
            if (z == z0 - 1 || z == z1 + 1) {
                set(x0 - 1, eaves - 1, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, true));
                set(x1 + 1, eaves - 1, z, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, true));
            }
        }
        for (int y = FLOOR + 1; y <= eaves + 5; y++) {
            set(9, y, 25, Blocks.BRICKS.defaultBlockState());
        }
        set(9, eaves + 6, 25, Blocks.CAMPFIRE.defaultBlockState());
        set(x1 + 1, eaves - 1, 20, hangingLantern());
        set(x1 + 1, eaves - 1, 24, hangingLantern());
        for (int z : new int[]{19, 20, 24, 25}) {
            set(x1 + 1, FLOOR + 1, z, Blocks.MOSS_BLOCK.defaultBlockState());
            Block flower = z % 3 == 0 ? Blocks.CORNFLOWER : z % 3 == 1 ? Blocks.POPPY : Blocks.AZURE_BLUET;
            set(x1 + 1, FLOOR + 2, z, flower.defaultBlockState());
        }
        set(x1 - 1, FLOOR + 1, 20, Blocks.BARREL.defaultBlockState());
        set(x1 - 1, FLOOR + 2, 20, Blocks.LANTERN.defaultBlockState());
        set(x1 - 1, FLOOR + 1, 24, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(x1 - 1, FLOOR + 2, 24, Blocks.LANTERN.defaultBlockState());
        set(x0 + 1, FLOOR + 1, 21, facing(Blocks.FURNACE, Direction.EAST).setValue(BlockStateProperties.LIT, true));
        box(x0 + 1, FLOOR + 1, 22, x0 + 1, FLOOR + 2, 23, Blocks.BOOKSHELF.defaultBlockState());
        set(x1 + 2, FLOOR + 1, 18, Blocks.BARREL.defaultBlockState());
        set(x1 + 2, FLOOR + 2, 18, Blocks.BARREL.defaultBlockState());
        set(x1 + 2, FLOOR + 1, 17, Blocks.BARREL.defaultBlockState());
    }

    /** South of the cottage: crop rows with a channel, a fence on two sides, hay, barrels and a composter. */
    private void farm() {
        int x0 = 9;
        int x1 = 16;
        int z0 = 28;
        int z1 = 33;
        carve(x0 - 1, FLOOR + 1, z0 - 1, x1 + 1, FLOOR + 4, z1 + 1);
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        CropBlock[] rows = {wheat, (CropBlock) Blocks.CARROTS, null, (CropBlock) Blocks.POTATOES, (CropBlock) Blocks.BEETROOTS, wheat};
        BlockState soil = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
        for (int z = z0; z <= z1; z++) {
            CropBlock crop = rows[z - z0];
            for (int x = x0; x <= x1; x++) {
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
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            set(x, FLOOR, z0 - 1, alongX);
            set(x, FLOOR, z1 + 1, alongX);
            set(x, FLOOR + 1, z1 + 1, fence(false, x < x1 + 1, false, x > x0 - 1));
        }
        for (int z = z0; z <= z1; z++) {
            set(x0 - 1, FLOOR, z, alongZ);
            set(x1 + 1, FLOOR, z, alongZ);
            set(x0 - 1, FLOOR + 1, z, fence(z > z0, false, true, false));
        }
        set(x0 - 1, FLOOR + 1, z1 + 1, fence(true, true, false, false));
        set(x1 + 1, FLOOR + 1, z1 + 1, Blocks.SPRUCE_FENCE.defaultBlockState().setValue(CrossCollisionBlock.WEST, true));
        set(x1 + 1, FLOOR + 2, z1 + 1, Blocks.LANTERN.defaultBlockState());
        set(x0 - 1, FLOOR + 2, z0, Blocks.LANTERN.defaultBlockState());
        set(x1 + 2, FLOOR + 1, 29, Blocks.HAY_BLOCK.defaultBlockState());
        set(x1 + 2, FLOOR + 1, 30, Blocks.HAY_BLOCK.defaultBlockState());
        set(x1 + 2, FLOOR + 2, 29, Blocks.HAY_BLOCK.defaultBlockState());
        set(x1 + 2, FLOOR + 1, 32, Blocks.COMPOSTER.defaultBlockState());
        set(x1 + 2, FLOOR + 1, 33, Blocks.BARREL.defaultBlockState());
        set(x1 + 2, FLOOR + 2, 33, Blocks.LANTERN.defaultBlockState());
        set(13, FLOOR + 1, 32, AIR);
        world.figures.add(new CaveWorld.Figure(13.5F, FLOOR + 0.9375F, 32.5F, 200.0F, false, 5,
                new ItemStack(Items.IRON_HOE), ItemStack.EMPTY, true));
    }

    /** A workshop dug into the west wall north of the cottage: furnaces, anvil, smithing table, a smith at work. */
    private void workshop() {
        carve(4, FLOOR + 1, 11, 11, FLOOR + 4, 16);
        box(4, FLOOR, 11, 11, FLOOR, 16, Blocks.SPRUCE_PLANKS.defaultBlockState());
        set(4, FLOOR + 1, 12, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(4, FLOOR + 1, 13, facing(Blocks.FURNACE, Direction.EAST).setValue(BlockStateProperties.LIT, true));
        set(4, FLOOR + 1, 14, facing(Blocks.BLAST_FURNACE, Direction.EAST).setValue(BlockStateProperties.LIT, true));
        set(4, FLOOR + 1, 15, Blocks.SMITHING_TABLE.defaultBlockState());
        set(4, FLOOR + 2, 13, Blocks.LANTERN.defaultBlockState());
        set(6, FLOOR + 1, 16, facing(Blocks.ANVIL, Direction.EAST));
        set(8, FLOOR + 1, 16, Blocks.GRINDSTONE.defaultBlockState());
        set(6, FLOOR + 1, 11, Blocks.BARREL.defaultBlockState());
        set(7, FLOOR + 1, 11, Blocks.BARREL.defaultBlockState());
        set(6, FLOOR + 2, 11, Blocks.BARREL.defaultBlockState());
        set(9, FLOOR + 1, 11, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        blockEntity(9, FLOOR + 1, 11, "minecraft:chest");
        BlockState post = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
        for (int z : new int[]{11, 16}) {
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                set(11, y, z, post);
            }
        }
        for (int z = 11; z <= 16; z++) {
            set(11, FLOOR + 4, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
            set(12, FLOOR + 4, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        }
        set(8, FLOOR + 3, 13, hangingLantern());
        set(8, FLOOR + 4, 13, Blocks.CHAIN.defaultBlockState());
        world.figures.add(new CaveWorld.Figure(6.5F, FLOOR + 1, 15.2F, 0.0F, false, 11,
                new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY, true));
    }

    /** Behind an amethyst-studded arch in the north wall: an enchanting table ringed by bookshelves and candles. */
    private void enchantingRoom() {
        carve(16, FLOOR + 1, 1, 22, FLOOR + 5, 8);
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
            for (int z = 1; z <= 8; z++) {
                set(16, y, z, random.nextFloat() < 0.3F ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : bricks);
                set(22, y, z, random.nextFloat() < 0.3F ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : bricks);
            }
            for (int x = 16; x <= 22; x++) {
                set(x, y, 1, bricks);
                set(x, y, 8, random.nextFloat() < 0.25F ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : bricks);
            }
        }
        box(16, FLOOR + 6, 1, 22, FLOOR + 6, 8, Blocks.DEEPSLATE_TILES.defaultBlockState());
        carve(18, FLOOR + 1, 8, 20, FLOOR + 3, 8);
        set(18, FLOOR + 3, 8, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
        set(20, FLOOR + 3, 8, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
        box(17, FLOOR, 2, 21, FLOOR, 7, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        set(19, FLOOR + 1, 4, Blocks.ENCHANTING_TABLE.defaultBlockState());
        blockEntity(19, FLOOR + 1, 4, "minecraft:enchanting_table");
        for (int z = 2; z <= 6; z++) {
            box(17, FLOOR + 1, z, 17, FLOOR + 2, z, Blocks.BOOKSHELF.defaultBlockState());
            box(21, FLOOR + 1, z, 21, FLOOR + 2, z, Blocks.BOOKSHELF.defaultBlockState());
        }
        box(18, FLOOR + 1, 2, 20, FLOOR + 2, 2, Blocks.BOOKSHELF.defaultBlockState());
        for (int[] c : new int[][]{{17, 3}, {17, 6}, {21, 2}, {21, 5}, {19, 2}}) {
            set(c[0], FLOOR + 3, c[1], Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(3))
                    .setValue(BlockStateProperties.LIT, true));
        }
        set(21, FLOOR + 3, 4, Blocks.LANTERN.defaultBlockState());
        for (int x = 15; x <= 23; x++) {
            for (int y = FLOOR + 1; y <= FLOOR + 6; y++) {
                if (random.nextFloat() < 0.35F && (x < 17 || x > 21 || y > FLOOR + 4) && !air(x, y, 8)) {
                    set(x, y, 8, random.nextFloat() < 0.2F ? Blocks.BUDDING_AMETHYST.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState());
                    if (air(x, y, 9) && random.nextFloat() < 0.6F) {
                        set(x, y, 9, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.SOUTH));
                    }
                }
            }
        }
    }

    /** A timbered side tunnel in the north wall, ending at a small diamond vein the player is digging out. */
    private void mineTunnel() {
        carve(28, FLOOR + 1, 1, 32, FLOOR + 4, 10);
        for (int x = 27; x <= 33; x++) {
            for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
                set(x, y, 0, random.nextFloat() < 0.1F ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState());
            }
        }
        for (int z = 1; z <= 9; z++) {
            for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
                if (!air(27, y, z)) {
                    set(27, y, z, Blocks.DEEPSLATE.defaultBlockState());
                }
                if (!air(33, y, z)) {
                    set(33, y, z, Blocks.DEEPSLATE.defaultBlockState());
                }
            }
        }
        BlockState diamond = Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
        set(30, FLOOR + 1, 0, diamond);
        set(30, FLOOR + 2, 0, diamond);
        set(31, FLOOR + 2, 0, diamond);
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int z : new int[]{8, 10}) {
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                set(28, y, z, post);
                set(32, y, z, post);
            }
            for (int x = 28; x <= 32; x++) {
                set(x, FLOOR + 4, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
            }
        }
        box(28, FLOOR, 1, 32, FLOOR, 9, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(28, FLOOR + 3, 4, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
        set(32, FLOOR + 3, 2, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST));
        set(28, FLOOR + 1, 6, Blocks.LANTERN.defaultBlockState());
        set(32, FLOOR + 1, 1, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(28, FLOOR + 1, 1, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(28, FLOOR + 2, 1, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        for (int z = 3; z <= 10; z++) {
            set(30, FLOOR + 1, z, Blocks.RAIL.defaultBlockState());
        }
        world.figures.add(new CaveWorld.Figure(30.5F, FLOOR + 1, 2.7F, 180.0F, true, 0,
                new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.TORCH), true));
    }

    /** By the east wall: friends round a campfire on stair benches, supplies stacked behind. */
    private void camp() {
        int fx = 38;
        int fz = 25;
        set(fx, FLOOR + 1, fz, Blocks.CAMPFIRE.defaultBlockState());
        set(fx, FLOOR, fz, Blocks.COBBLESTONE.defaultBlockState());
        set(fx - 3, FLOOR + 1, fz, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        set(fx - 3, FLOOR + 1, fz + 1, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        set(fx, FLOOR + 1, fz + 3, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        set(fx + 1, FLOOR + 1, fz + 3, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        set(fx + 2, FLOOR + 1, fz - 3, Blocks.BARREL.defaultBlockState());
        set(fx + 3, FLOOR + 1, fz - 3, Blocks.BARREL.defaultBlockState());
        set(fx + 2, FLOOR + 2, fz - 3, Blocks.BARREL.defaultBlockState());
        float[][] friends = {{36.2F, 24.0F}, {38.6F, 27.8F}, {40.2F, 23.8F}};
        ItemStack[] hands = {new ItemStack(Items.TORCH), new ItemStack(Items.BREAD), new ItemStack(Items.IRON_SHOVEL)};
        int[] skins = {2, 9, 14};
        for (int i = 0; i < friends.length; i++) {
            float yaw = (float) Math.toDegrees(Mth.atan2(-(fx + 0.5F - friends[i][0]), fz + 0.5F - friends[i][1]));
            world.figures.add(new CaveWorld.Figure(friends[i][0], FLOOR + 1, friends[i][1], yaw, false, skins[i], hands[i],
                    ItemStack.EMPTY, false));
        }
    }

    /** Stone paths between everything, three wide, a lamp set into the ground every few steps. */
    private void paths() {
        int[][] legs = {{15, 22, 20, 22}, {20, 22, 20, 11}, {20, 13, 12, 14}, {20, 11, 30, 11}, {20, 22, 21, 34},
            {21, 34, 34, 35}, {34, 35, 37, 28}};
        for (int[] leg : legs) {
            int steps = Math.max(Math.abs(leg[2] - leg[0]), Math.abs(leg[3] - leg[1]));
            for (int i = 0; i <= steps; i++) {
                float t = steps == 0 ? 0.0F : (float) i / steps;
                int px = Math.round(Mth.lerp(t, leg[0], leg[2]));
                int pz = Math.round(Mth.lerp(t, leg[1], leg[3]));
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        pave(px + dx, pz + dz, dx == 0 && dz == 0 && i % 5 == 2);
                    }
                }
            }
        }
    }

    private void pave(int x, int z, boolean lamp) {
        BlockState under = get(x, FLOOR, z);
        if (!(under.is(Blocks.MOSS_BLOCK) || under.is(Blocks.GRAVEL) || under.is(Blocks.COARSE_DIRT) || under.is(Blocks.STONE)
                || under.is(Blocks.MOSSY_COBBLESTONE)) || !(air(x, FLOOR + 1, z) || get(x, FLOOR + 1, z).is(Blocks.MOSS_CARPET)
                || get(x, FLOOR + 1, z).is(Blocks.FERN))) {
            return;
        }
        set(x, FLOOR + 1, z, AIR);
        if (lamp) {
            set(x, FLOOR, z, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
            return;
        }
        float r = random.nextFloat();
        Block block = r < 0.35F ? Blocks.POLISHED_ANDESITE : r < 0.55F ? Blocks.STONE_BRICKS : r < 0.7F ? Blocks.DIRT_PATH
                : r < 0.82F ? Blocks.MOSSY_STONE_BRICKS : r < 0.92F ? Blocks.GRAVEL : Blocks.CRACKED_STONE_BRICKS;
        set(x, FLOOR, z, block.defaultBlockState());
    }

    /** A cherry tree: a leaning trunk and a broad, drooping canopy of blossom. */
    private void cherry(int x, int z, int height, float radius) {
        BlockState trunk = Blocks.CHERRY_LOG.defaultBlockState();
        float lean = random.nextFloat() * Mth.TWO_PI;
        float tx = x;
        float tz = z;
        for (int y = FLOOR + 1; y <= FLOOR + height; y++) {
            set(Math.round(tx), y, Math.round(tz), trunk);
            if (y > FLOOR + 3) {
                tx += Mth.cos(lean) * 0.35F;
                tz += Mth.sin(lean) * 0.35F;
            }
        }
        set(x, FLOOR, z, Blocks.ROOTED_DIRT.defaultBlockState());
        int top = FLOOR + height;
        int cx = Math.round(tx);
        int cz = Math.round(tz);
        BlockState blossom = leaves(Blocks.CHERRY_LEAVES);
        for (int dy = -3; dy <= 2; dy++) {
            float r = radius * (dy < 0 ? 1.0F + dy * 0.12F : 1.0F - dy * 0.3F);
            for (int dz = -6; dz <= 6; dz++) {
                for (int dx = -6; dx <= 6; dx++) {
                    float d = Mth.sqrt(dx * dx + dz * dz) + random.nextFloat() * 0.9F;
                    if (d < r && air(cx + dx, top + dy, cz + dz) && (dy > -3 || d > r - 1.6F)) {
                        set(cx + dx, top + dy, cz + dz, blossom);
                    }
                }
            }
        }
        for (int i = 0; i < 18; i++) {
            int dx = random.nextInt(9) - 4;
            int dz = random.nextInt(9) - 4;
            int y = top - 3;
            if (get(cx + dx, y, cz + dz).is(Blocks.CHERRY_LEAVES) && air(cx + dx, y - 1, cz + dz)) {
                set(cx + dx, y - 1, cz + dz, blossom);
            }
        }
        for (int i = 0; i < 14; i++) {
            int px = x + random.nextInt(9) - 4;
            int pz = z + random.nextInt(9) - 4;
            if (air(px, FLOOR + 1, pz) && get(px, FLOOR, pz).is(Blocks.MOSS_BLOCK)) {
                set(px, FLOOR + 1, pz, Blocks.PINK_PETALS.defaultBlockState());
            }
        }
    }

    /** Two timber bridges spanning the shaft high up, lanterns under their beams and glow berries hanging below. */
    private void bridges() {
        bridge(true, 14, 25, 27);
        bridge(false, 20, 31, 13);
    }

    /** A bridge along x ({@code alongX}) or z, three wide centred on {@code across}, its deck at {@code y}. */
    private void bridge(boolean alongX, int across, int y, int person) {
        int length = alongX ? SX : SZ;
        int start = -1;
        int end = -1;
        for (int i = 0; i < length; i++) {
            boolean open = alongX ? air(i, y, across) : air(across, y, i);
            if (open && start < 0) {
                start = i;
            }
            if (open) {
                end = i;
            }
        }
        if (start < 0) {
            return;
        }
        BlockState deck = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int i = start - 1; i <= end + 1; i++) {
            for (int w = -1; w <= 1; w++) {
                int x = alongX ? i : across + w;
                int z = alongX ? across + w : i;
                set(x, y, z, w == 0 || random.nextFloat() < 0.85F ? deck : Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                if (w != 0) {
                    boolean prev = i > start - 1;
                    boolean next = i < end + 1;
                    set(x, y + 1, z, alongX ? fence(false, next, false, prev) : fence(prev, false, next, false));
                }
            }
            if ((i - start) % 5 == 0) {
                for (int w = -2; w <= 2; w++) {
                    int x = alongX ? i : across + w;
                    int z = alongX ? across + w : i;
                    set(x, y - 1, z, log(Blocks.DARK_OAK_LOG, alongX ? Direction.Axis.Z : Direction.Axis.X));
                }
                set(alongX ? i : across, y - 2, alongX ? across : i, hangingLantern());
            }
            for (int w : new int[]{-1, 1}) {
                if (random.nextFloat() < 0.45F) {
                    int x = alongX ? i : across + w;
                    int z = alongX ? across + w : i;
                    int top = y - 1;
                    if (!air(x, top, z)) {
                        continue;
                    }
                    int vine = 2 + random.nextInt(7);
                    for (int k = 0; k < vine && air(x, top - k, z); k++) {
                        Block block = k == vine - 1 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT;
                        set(x, top - k, z, block.defaultBlockState().setValue(CaveVines.BERRIES, random.nextFloat() < 0.55F));
                    }
                }
            }
        }
        float px = alongX ? person + 0.5F : across + 0.5F;
        float pz = alongX ? across + 0.5F : person + 0.5F;
        world.figures.add(new CaveWorld.Figure(px, y + 1, pz, alongX ? 0.0F : -90.0F, false, 7, ItemStack.EMPTY, ItemStack.EMPTY, false));
    }

    /** A patch of amethyst in the wall, found by heading out from the centre along {@code (dx, dz)} at height {@code y}. */
    private void amethyst(float dx, float dz, int y) {
        float x = CX;
        float z = CZ;
        while (world.contains((int) x, y, (int) z) && air((int) x, y, (int) z)) {
            x += dx * 0.5F;
            z += dz * 0.5F;
        }
        int cx = (int) x;
        int cz = (int) z;
        for (int oy = -4; oy <= 4; oy++) {
            for (int oz = -4; oz <= 4; oz++) {
                for (int ox = -4; ox <= 4; ox++) {
                    float d = Mth.sqrt(ox * ox + oy * oy * 1.4F + oz * oz) + random.nextFloat() * 0.8F;
                    if (d < 4.2F && rock(get(cx + ox, y + oy, cz + oz))) {
                        set(cx + ox, y + oy, cz + oz, random.nextFloat() < 0.25F ? Blocks.BUDDING_AMETHYST.defaultBlockState()
                                : Blocks.AMETHYST_BLOCK.defaultBlockState());
                    }
                }
            }
        }
        for (int oy = -5; oy <= 5; oy++) {
            for (int oz = -5; oz <= 5; oz++) {
                for (int ox = -5; ox <= 5; ox++) {
                    int px = cx + ox;
                    int py = y + oy;
                    int pz = cz + oz;
                    if (!air(px, py, pz) || random.nextFloat() > 0.55F) {
                        continue;
                    }
                    for (Direction side : Direction.values()) {
                        BlockState support = get(px - side.getStepX(), py - side.getStepY(), pz - side.getStepZ());
                        if (support.is(Blocks.AMETHYST_BLOCK) || support.is(Blocks.BUDDING_AMETHYST)) {
                            Block crystal = random.nextFloat() < 0.6F ? Blocks.AMETHYST_CLUSTER : Blocks.LARGE_AMETHYST_BUD;
                            set(px, py, pz, crystal.defaultBlockState().setValue(AmethystClusterBlock.FACING, side));
                            break;
                        }
                    }
                }
            }
        }
    }

    /** Warm lamps set flush into the rock all the way up the walls. */
    private void wallLights() {
        Block[] lamps = {Blocks.SHROOMLIGHT, Blocks.SHROOMLIGHT, Blocks.SHROOMLIGHT, Blocks.OCHRE_FROGLIGHT};
        for (int z = 0; z < SZ; z++) {
            for (int y = FLOOR + 3; y < SY - 2; y++) {
                for (int x = 0; x < SX; x++) {
                    if (!rock(get(x, y, z)) || random.nextFloat() > 0.02F) {
                        continue;
                    }
                    if (air(x + 1, y, z) || air(x - 1, y, z) || air(x, y, z + 1) || air(x, y, z - 1) || air(x, y - 1, z)) {
                        set(x, y, z, lamps[random.nextInt(lamps.length)].defaultBlockState());
                    }
                }
            }
        }
    }

    /** Moss creeping up the foot of the walls and vines trailing down them. */
    private void wallGreenery() {
        for (int z = 0; z < SZ; z++) {
            for (int x = 0; x < SX; x++) {
                for (int y = FLOOR + 1; y < SY - 2; y++) {
                    BlockState state = get(x, y, z);
                    if (!rock(state)) {
                        continue;
                    }
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        int ax = x + side.getStepX();
                        int az = z + side.getStepZ();
                        if (!air(ax, y, az)) {
                            continue;
                        }
                        if (y <= FLOOR + 3 && random.nextFloat() < 0.45F) {
                            set(x, y, z, Blocks.MOSS_BLOCK.defaultBlockState());
                        } else if (y > FLOOR + 6 && random.nextFloat() < 0.025F) {
                            BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(side.getOpposite()), true);
                            int length = 2 + random.nextInt(7);
                            for (int k = 0; k < length && air(ax, y - k, az) && y - k > FLOOR + 1; k++) {
                                set(ax, y - k, az, vine);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    /** Glow berries hanging from the roof of the shaft. */
    private void ceilingVines() {
        for (int i = 0; i < 260; i++) {
            int x = 2 + random.nextInt(SX - 4);
            int z = 2 + random.nextInt(SZ - 4);
            int top = ceiling(x, FLOOR + 1, z);
            if (top < 36) {
                continue;
            }
            int length = Math.min(top - 34, 2 + random.nextInt(10));
            for (int k = 0; k < length; k++) {
                Block block = k == length - 1 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT;
                set(x, top - k, z, block.defaultBlockState().setValue(CaveVines.BERRIES, random.nextFloat() < 0.5F));
            }
        }
    }
}
