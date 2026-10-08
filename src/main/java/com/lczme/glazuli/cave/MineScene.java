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
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.joml.Vector3f;

/**
 * An underground survival base, like the cave a single chamber with a corner per menu: the path from the rail tunnel
 * up to a timber house built against the back wall (title), the player digging out a small diamond vein at the end of
 * a branch tunnel (singleplayer), friends round a campfire (multiplayer), the workshop (options), an enchanting nook
 * (mods) and a farm watered by a waterfall (everything else).
 */
final class MineScene {
    private static final int SX = 64;
    private static final int SY = 36;
    private static final int SZ = 60;
    /** Top of the floor; the air starts one above. */
    private static final int FLOOR = 8;
    private static final float HALL_X = 32.0F;
    private static final float HALL_Z = 28.0F;
    private static final float HALL_RX = 26.0F;
    private static final float HALL_RZ = 24.0F;
    private static final float HALL_HEIGHT = 14.0F;
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
            {33.5F, 11.4F, 25.5F, 31.0F, 11.6F, 40.0F},
            {7.4F, 10.6F, 20.4F, 3.7F, 10.1F, 19.4F},
            {39.5F, 11.5F, 25.0F, 44.5F, 9.8F, 30.5F},
            {45.5F, 11.0F, 18.0F, 55.0F, 9.8F, 18.0F},
            {45.0F, 11.2F, 35.5F, 45.0F, 9.8F, 43.0F},
            {25.0F, 12.5F, 25.0F, 13.0F, 10.0F, 34.0F},
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

    private void set(int x, int y, int z, BlockState state) {
        if (world.contains(x, y, z)) {
            world.set(x, y, z, state);
        }
    }

    private BlockState get(int x, int y, int z) {
        return world.contains(x, y, z) ? world.get(x, y, z) : Blocks.STONE.defaultBlockState();
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

    private void air(int x0, int y0, int z0, int x1, int y1, int z1) {
        box(x0, y0, z0, x1, y1, z1, AIR);
    }

    private void blockEntity(int x, int y, int z, String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        world.blockEntities.put(new BlockPos(x, y, z), tag);
    }

    /** The highest air above the floor at a column, or -1 when the floor itself is covered. */
    private int ceiling(int x, int z) {
        if (!get(x, FLOOR + 1, z).isAir()) {
            return -1;
        }
        int y = FLOOR + 1;
        while (y < SY - 1 && get(x, y + 1, z).isAir()) {
            y++;
        }
        return y;
    }

    private boolean bareRock(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE);
    }

    private static BlockState log(Block block, Direction.Axis axis) {
        return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static BlockState hangingLantern() {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    private static BlockState facing(Block block, Direction direction) {
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction);
    }

    private void fill(long seed) {
        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed ^ 0x3A1EL));
        for (int z = 0; z < SZ; z++) {
            for (int y = 0; y < SY; y++) {
                for (int x = 0; x < SX; x++) {
                    double n = noise.noise(x * 0.11, y * 0.11, z * 0.11);
                    boolean deep = y < FLOOR + 2 + n * 3.0 || x < 13 && z > 13 && z < 25 && y < FLOOR + 7;
                    BlockState rock = deep ? Blocks.DEEPSLATE.defaultBlockState()
                            : n > 0.42 ? Blocks.ANDESITE.defaultBlockState()
                            : n < -0.45 ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    world.set(x, y, z, rock);
                }
            }
        }
        ores();
        hall(noise);
        railTunnel();
        branchMine();
        moss();
        waterfall();
        farm();
        house();
        enchantingNook();
        camp();
        workshop();
        path();
        lanterns();
        glowBerries();
        dripstone();
    }

    private void ores() {
        Block[] kinds = {Blocks.COAL_ORE, Blocks.COAL_ORE, Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE,
            Blocks.GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE};
        for (int i = 0; i < 520; i++) {
            int cx = random.nextInt(SX);
            int cy = 4 + random.nextInt(SY - 8);
            int cz = random.nextInt(SZ);
            Block kind = kinds[random.nextInt(kinds.length)];
            int size = 2 + random.nextInt(4);
            for (int j = 0; j < size; j++) {
                int x = cx + random.nextInt(3) - 1;
                int y = cy + random.nextInt(3) - 1;
                int z = cz + random.nextInt(3) - 1;
                if (world.contains(x, y, z)) {
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

    /** The chamber: a flat floor under a rough dome, scattered with gravel and cobble. */
    private void hall(ImprovedNoise noise) {
        BlockState[] floor = {Blocks.GRAVEL.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.COBBLED_DEEPSLATE.defaultBlockState()};
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

    /** The timbered rail tunnel in from the south, its rails ending at a chest and barrels inside the hall. */
    private void railTunnel() {
        air(30, FLOOR + 1, 0, 34, FLOOR + 4, 10);
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState beam = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X);
        for (int z : new int[]{2, 6, 9}) {
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
        air(3, FLOOR + 1, 17, 12, FLOOR + 4, 21);
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState beam = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z);
        for (int x : new int[]{9, 11}) {
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                set(x, y, 17, post);
                set(x, y, 21, post);
            }
            for (int z = 17; z <= 21; z++) {
                set(x, FLOOR + 4, z, beam);
            }
        }
        for (int z = 16; z <= 22; z++) {
            for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
                BlockState face = random.nextFloat() < 0.12F ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState()
                        : random.nextFloat() < 0.1F ? Blocks.TUFF.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
                set(2, y, z, face);
            }
        }
        BlockState diamond = Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
        set(2, FLOOR + 1, 19, diamond);
        set(2, FLOOR + 2, 19, diamond);
        set(2, FLOOR + 2, 20, diamond);
        set(6, FLOOR + 3, 17, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        set(4, FLOOR + 3, 21, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
        set(7, FLOOR + 1, 21, Blocks.LANTERN.defaultBlockState());
        set(3, FLOOR + 1, 21, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(4, FLOOR + 1, 17, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(3, FLOOR + 1, 17, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(3, FLOOR + 2, 17, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        world.figures.add(new CaveWorld.Figure(3.7F, FLOOR + 1, 19.4F, 90.0F, true, 0,
                new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.TORCH), true));
    }

    /** Moss creeping over the floor of the damp west side, round the waterfall. */
    private void moss() {
        for (int z = 24; z <= 44; z++) {
            for (int x = 5; x <= 24; x++) {
                float d = Mth.sqrt((x - 9.0F) * (x - 9.0F) + (z - 34.0F) * (z - 34.0F) * 0.6F);
                if (d > 12.0F || random.nextFloat() > 1.1F - d / 12.0F || !bareRock(get(x, FLOOR, z)) || !get(x, FLOOR + 1, z).isAir()) {
                    continue;
                }
                set(x, FLOOR, z, Blocks.MOSS_BLOCK.defaultBlockState());
                if (random.nextFloat() < 0.25F) {
                    set(x, FLOOR + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
    }

    /** Water falling from a crack in the west wall into a pool, a channel leading off to the farm. */
    private void waterfall() {
        for (int z = 31; z <= 37; z++) {
            for (int x = 6; x <= 10; x++) {
                float u = (x + 0.5F - 8.5F) / 2.6F;
                float v = (z + 0.5F - 34.0F) / 3.4F;
                float r2 = u * u + v * v;
                if (r2 < 1.0F && get(x, FLOOR + 1, z).isAir() || x == 7 && (z == 33 || z == 34)) {
                    set(x, FLOOR + 1, z, AIR);
                    set(x, FLOOR, z, Blocks.WATER.defaultBlockState());
                    set(x, FLOOR - 1, z, Blocks.WATER.defaultBlockState());
                } else if (r2 < 1.5F && get(x, FLOOR + 1, z).isAir()) {
                    set(x, FLOOR, z, (random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE : Blocks.MOSS_BLOCK).defaultBlockState());
                    set(x, FLOOR + 1, z, AIR);
                }
            }
        }
        BlockState falling = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
        for (int z = 33; z <= 34; z++) {
            int top = Math.min(FLOOR + 8, Math.max(FLOOR + 4, ceiling(7, z)));
            for (int y = FLOOR + 1; y <= top; y++) {
                set(7, y, z, falling);
            }
            set(6, top, z, Blocks.WATER.defaultBlockState());
            set(6, top + 1, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            set(7, top + 1, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        }
        set(8, FLOOR - 1, 34, Blocks.SEA_LANTERN.defaultBlockState());
        set(9, FLOOR - 1, 32, Blocks.SEA_LANTERN.defaultBlockState());
        set(10, FLOOR + 1, 31, Blocks.LANTERN.defaultBlockState());
        set(10, FLOOR, 31, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        for (int z = 32; z <= 36; z += 2) {
            int top = ceiling(9, z);
            for (int k = 0; top > 0 && k < 3 && top - k > FLOOR + 4; k++) {
                Block vine = k == 2 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT;
                set(9, top - k, z, vine.defaultBlockState().setValue(CaveVines.BERRIES, true));
            }
        }
        int bloom = ceiling(10, 35);
        if (bloom > 0) {
            set(10, bloom, 35, Blocks.SPORE_BLOSSOM.defaultBlockState());
        }
        for (int x = 11; x <= 20; x++) {
            set(x, FLOOR, 33, Blocks.WATER.defaultBlockState());
            set(x, FLOOR + 1, 33, AIR);
        }
        set(11, FLOOR + 1, 29, Blocks.AZALEA.defaultBlockState());
        set(8, FLOOR + 1, 39, Blocks.FLOWERING_AZALEA.defaultBlockState());
        set(8, FLOOR, 39, Blocks.MOSS_BLOCK.defaultBlockState());
        set(11, FLOOR, 29, Blocks.MOSS_BLOCK.defaultBlockState());
    }

    /** Crops either side of the channel, a log border, lanterns on fence posts, hay and a composter by the gate. */
    private void farm() {
        CropBlock[] rows = {(CropBlock) Blocks.WHEAT, (CropBlock) Blocks.CARROTS, (CropBlock) Blocks.POTATOES,
            (CropBlock) Blocks.BEETROOTS};
        BlockState soil = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
        for (int z = 29; z <= 37; z++) {
            if (z == 33) {
                continue;
            }
            CropBlock crop = rows[(z < 33 ? z - 29 : z - 30) / 2];
            for (int x = 12; x <= 20; x++) {
                set(x, FLOOR, z, soil);
                int age = Math.max(0, crop.getMaxAge() - random.nextInt(3));
                set(x, FLOOR + 1, z, crop.getStateForAge(age));
            }
        }
        BlockState alongX = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X);
        BlockState alongZ = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z);
        for (int x = 11; x <= 21; x++) {
            set(x, FLOOR, 28, alongX);
            set(x, FLOOR, 38, alongX);
            set(x, FLOOR + 1, 28, AIR);
            set(x, FLOOR + 1, 38, AIR);
        }
        for (int z = 29; z <= 37; z++) {
            if (z != 33) {
                set(11, FLOOR, z, alongZ);
            }
            set(21, FLOOR, z, alongZ);
            set(11, FLOOR + 1, z, AIR);
            set(21, FLOOR + 1, z, AIR);
        }
        for (int[] corner : new int[][]{{11, 28}, {21, 28}, {11, 38}, {21, 38}}) {
            set(corner[0], FLOOR + 1, corner[1], Blocks.SPRUCE_FENCE.defaultBlockState());
            set(corner[0], FLOOR + 2, corner[1], Blocks.LANTERN.defaultBlockState());
        }
        set(23, FLOOR + 1, 36, Blocks.HAY_BLOCK.defaultBlockState());
        set(23, FLOOR + 1, 37, Blocks.HAY_BLOCK.defaultBlockState());
        set(23, FLOOR + 2, 37, Blocks.HAY_BLOCK.defaultBlockState());
        set(22, FLOOR + 1, 30, Blocks.COMPOSTER.defaultBlockState());
        set(17, FLOOR + 1, 35, AIR);
        world.figures.add(new CaveWorld.Figure(17.5F, FLOOR + 0.9375F, 35.5F, 160.0F, false, 5,
                new ItemStack(Items.IRON_HOE), ItemStack.EMPTY, true));
    }

    /**
     * The house against the north wall: cobblestone footing, spruce frame and planks, glass windows lit from inside,
     * a dark oak gable roof with a chimney running up into the rock, and a porch with lanterns.
     */
    private void house() {
        air(24, FLOOR + 1, 36, 40, FLOOR + 10, 48);
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState post = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState paneX = Blocks.GLASS_PANE.defaultBlockState().setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true);
        BlockState paneZ = Blocks.GLASS_PANE.defaultBlockState().setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true);
        box(25, FLOOR, 40, 39, FLOOR, 46, cobble);
        box(26, FLOOR, 41, 38, FLOOR, 45, planks);
        for (int x = 25; x <= 39; x++) {
            for (int z : new int[]{40, 46}) {
                set(x, FLOOR + 1, z, cobble);
                for (int y = FLOOR + 2; y <= FLOOR + 4; y++) {
                    set(x, y, z, planks);
                }
            }
        }
        for (int z = 41; z <= 45; z++) {
            for (int x : new int[]{25, 39}) {
                set(x, FLOOR + 1, z, cobble);
                for (int y = FLOOR + 2; y <= FLOOR + 4; y++) {
                    set(x, y, z, z == 43 && y <= FLOOR + 3 ? paneZ : planks);
                }
            }
        }
        for (int[] p : new int[][]{{25, 40}, {30, 40}, {34, 40}, {39, 40}, {25, 46}, {39, 46}}) {
            for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
                set(p[0], y, p[1], post);
            }
        }
        for (int x : new int[]{27, 28, 36, 37}) {
            set(x, FLOOR + 2, 40, paneX);
            set(x, FLOOR + 3, 40, paneX);
        }
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        set(32, FLOOR + 1, 40, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(32, FLOOR + 2, 40, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        BlockState beamX = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X);
        BlockState beamZ = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z);
        int eaves = FLOOR + 5;
        for (int x = 25; x <= 39; x++) {
            set(x, eaves, 40, beamX);
            set(x, eaves, 46, beamX);
        }
        for (int z = 41; z <= 45; z++) {
            set(25, eaves, z, beamZ);
            set(39, eaves, z, beamZ);
        }
        for (int k = 1; k <= 2; k++) {
            for (int z = 40 + k; z <= 46 - k; z++) {
                set(25, eaves + k, z, planks);
                set(39, eaves + k, z, planks);
            }
        }
        BlockState south = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        BlockState north = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        for (int x = 24; x <= 40; x++) {
            for (int k = 0; k <= 3; k++) {
                set(x, eaves + k, 39 + k, south);
                set(x, eaves + k, 47 - k, north);
            }
            set(x, eaves + 3, 43, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            set(x, eaves + 4, 43, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        }
        for (int y = eaves; y < SY && (y <= eaves + 4 || get(37, y, 45).isAir()); y++) {
            set(37, y, 45, cobble);
        }
        set(37, FLOOR + 1, 45, facing(Blocks.FURNACE, Direction.SOUTH).setValue(BlockStateProperties.LIT, true));
        set(27, FLOOR + 1, 44, Blocks.BARREL.defaultBlockState());
        set(27, FLOOR + 2, 44, Blocks.LANTERN.defaultBlockState());
        set(35, FLOOR + 1, 44, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(36, FLOOR + 2, 42, Blocks.LANTERN.defaultBlockState());
        set(36, FLOOR + 1, 42, Blocks.BARREL.defaultBlockState());
        set(29, FLOOR + 1, 45, Blocks.BOOKSHELF.defaultBlockState());
        set(30, FLOOR + 1, 45, Blocks.BOOKSHELF.defaultBlockState());
        box(28, FLOOR, 37, 36, FLOOR, 39, planks);
        for (int x : new int[]{28, 36}) {
            set(x, FLOOR + 1, 37, Blocks.SPRUCE_FENCE.defaultBlockState());
            set(x, FLOOR + 2, 37, Blocks.LANTERN.defaultBlockState());
        }
        for (int x : new int[]{29, 35}) {
            set(x, eaves - 1, 39, hangingLantern());
        }
        set(26, FLOOR, 39, Blocks.MOSS_BLOCK.defaultBlockState());
        set(26, FLOOR + 1, 39, Blocks.FLOWERING_AZALEA.defaultBlockState());
        set(38, FLOOR, 39, Blocks.MOSS_BLOCK.defaultBlockState());
        set(38, FLOOR + 1, 39, Blocks.AZALEA.defaultBlockState());
        set(29, FLOOR + 1, 39, Blocks.POTTED_RED_TULIP.defaultBlockState());
        set(35, FLOOR + 1, 39, Blocks.BARREL.defaultBlockState());
        set(35, FLOOR + 2, 39, Blocks.POTTED_FERN.defaultBlockState());
    }

    /** East of the house, a stone-brick nook: an enchanting table ringed by bookshelves, candles burning on top. */
    private void enchantingNook() {
        air(42, FLOOR + 1, 38, 48, FLOOR + 5, 46);
        BlockState[] bricks = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
            Blocks.CRACKED_STONE_BRICKS.defaultBlockState()};
        for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
            for (int z = 39; z <= 46; z++) {
                set(42, y, z, bricks[random.nextInt(3)]);
                set(48, y, z, bricks[random.nextInt(3)]);
            }
            for (int x = 42; x <= 48; x++) {
                set(x, y, 46, bricks[random.nextInt(3)]);
            }
        }
        box(43, FLOOR, 39, 47, FLOOR, 45, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        set(45, FLOOR + 1, 42, Blocks.ENCHANTING_TABLE.defaultBlockState());
        blockEntity(45, FLOOR + 1, 42, "minecraft:enchanting_table");
        for (int z = 40; z <= 44; z++) {
            for (int y = FLOOR + 1; y <= FLOOR + 2; y++) {
                set(43, y, z, Blocks.BOOKSHELF.defaultBlockState());
                set(47, y, z, Blocks.BOOKSHELF.defaultBlockState());
            }
        }
        for (int x = 44; x <= 46; x++) {
            set(x, FLOOR + 1, 44, Blocks.BOOKSHELF.defaultBlockState());
            set(x, FLOOR + 2, 44, Blocks.BOOKSHELF.defaultBlockState());
        }
        for (int[] c : new int[][]{{43, 40}, {43, 43}, {47, 41}, {47, 44}, {45, 44}}) {
            set(c[0], FLOOR + 3, c[1], Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(3))
                    .setValue(BlockStateProperties.LIT, true));
        }
        set(47, FLOOR + 3, 42, Blocks.LANTERN.defaultBlockState());
        set(43, FLOOR + 1, 45, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
        set(47, FLOOR + 1, 45, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
        set(43, FLOOR + 1, 39, Blocks.POTTED_BLUE_ORCHID.defaultBlockState());
    }

    /** South-east of the house: friends round a campfire, logs to sit on, supplies stacked behind. */
    private void camp() {
        set(44, FLOOR + 1, 30, Blocks.CAMPFIRE.defaultBlockState());
        BlockState bench = log(Blocks.SPRUCE_LOG, Direction.Axis.Z);
        for (int z = 29; z <= 31; z++) {
            set(40, FLOOR + 1, z, bench);
        }
        set(49, FLOOR + 1, 33, Blocks.BARREL.defaultBlockState());
        set(50, FLOOR + 1, 33, Blocks.BARREL.defaultBlockState());
        set(49, FLOOR + 2, 33, Blocks.BARREL.defaultBlockState());
        set(50, FLOOR + 1, 32, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        blockEntity(50, FLOOR + 1, 32, "minecraft:chest");
        float fx = 44.5F;
        float fz = 30.5F;
        float[][] friends = {{42.0F, 30.6F}, {44.6F, 33.1F}, {47.0F, 29.4F}};
        ItemStack[] hands = {new ItemStack(Items.TORCH), new ItemStack(Items.BREAD), new ItemStack(Items.IRON_SHOVEL)};
        int[] skins = {2, 9, 14};
        for (int i = 0; i < friends.length; i++) {
            float yaw = (float) Math.toDegrees(Mth.atan2(-(fx - friends[i][0]), fz - friends[i][1]));
            world.figures.add(new CaveWorld.Figure(friends[i][0], FLOOR + 1, friends[i][1], yaw, false, skins[i], hands[i],
                    ItemStack.EMPTY, false));
        }
    }

    /** A lean-to against the east wall: furnaces, smithing table, anvil and grindstone, a smith at work. */
    private void workshop() {
        air(50, FLOOR + 1, 13, 56, FLOOR + 4, 23);
        set(56, FLOOR + 1, 15, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(56, FLOOR + 1, 16, facing(Blocks.FURNACE, Direction.WEST).setValue(BlockStateProperties.LIT, true));
        set(56, FLOOR + 1, 17, facing(Blocks.BLAST_FURNACE, Direction.WEST).setValue(BlockStateProperties.LIT, true));
        set(56, FLOOR + 1, 18, facing(Blocks.SMOKER, Direction.WEST));
        set(56, FLOOR + 1, 19, Blocks.SMITHING_TABLE.defaultBlockState());
        set(56, FLOOR + 2, 16, Blocks.LANTERN.defaultBlockState());
        set(54, FLOOR + 1, 21, facing(Blocks.ANVIL, Direction.NORTH));
        set(52, FLOOR + 1, 21, Blocks.GRINDSTONE.defaultBlockState());
        set(52, FLOOR + 1, 14, Blocks.BARREL.defaultBlockState());
        set(53, FLOOR + 1, 14, Blocks.BARREL.defaultBlockState());
        set(52, FLOOR + 2, 14, Blocks.BARREL.defaultBlockState());
        set(55, FLOOR + 1, 14, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        blockEntity(55, FLOOR + 1, 14, "minecraft:chest");
        BlockState post = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
        for (int z : new int[]{13, 23}) {
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                set(51, y, z, post);
            }
        }
        box(51, FLOOR + 4, 13, 56, FLOOR + 4, 23, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        set(54, FLOOR + 3, 18, hangingLantern());
        world.figures.add(new CaveWorld.Figure(54.5F, FLOOR + 1, 20.2F, 180.0F, false, 11,
                new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY, true));
    }

    /** A worn path from the end of the rails up to the porch. */
    private void path() {
        BlockState[] worn = {Blocks.DIRT_PATH.defaultBlockState(), Blocks.DIRT_PATH.defaultBlockState(),
            Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()};
        for (int z = 16; z <= 36; z++) {
            for (int x = 31; x <= 33; x++) {
                if ((x == 32 || random.nextFloat() < 0.7F) && get(x, FLOOR + 1, z).isAir()) {
                    set(x, FLOOR, z, worn[random.nextInt(worn.length)]);
                }
            }
        }
    }

    /** Lanterns on chains from the dome, high enough that the camera passes below them. */
    private void lanterns() {
        int[][] spots = {{32, 21}, {24, 25}, {40, 23}, {16, 31}, {44, 34}, {32, 31}, {50, 19}, {14, 22}};
        for (int[] spot : spots) {
            int x = spot[0];
            int z = spot[1];
            int top = ceiling(x, z);
            int lantern = Math.max(FLOOR + 7, top - 3);
            if (top < 0 || lantern >= top) {
                continue;
            }
            for (int y = lantern + 1; y <= top; y++) {
                set(x, y, z, Blocks.CHAIN.defaultBlockState());
            }
            set(x, lantern, z, hangingLantern());
        }
    }

    /** Glow berry vines hanging from the high parts of the dome. */
    private void glowBerries() {
        for (int i = 0; i < 60; i++) {
            int x = 6 + random.nextInt(SX - 12);
            int z = 4 + random.nextInt(SZ - 12);
            int top = ceiling(x, z);
            if (top < FLOOR + 9) {
                continue;
            }
            int length = Math.min(top - (FLOOR + 7), 1 + random.nextInt(4));
            for (int k = 0; k < length; k++) {
                int y = top - k;
                Block block = k == length - 1 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT;
                set(x, y, z, block.defaultBlockState().setValue(CaveVines.BERRIES, random.nextFloat() < 0.45F));
            }
        }
    }

    /** Dripstone round the edges of the hall: stalactites from the lower ceiling, stalagmites up from bare floor. */
    private void dripstone() {
        for (int i = 0; i < 90; i++) {
            int x = 4 + random.nextInt(SX - 8);
            int z = 3 + random.nextInt(SZ - 6);
            float u = (x + 0.5F - HALL_X) / HALL_RX;
            float v = (z + 0.5F - HALL_Z) / HALL_RZ;
            if (u * u + v * v < 0.62F) {
                continue;
            }
            int top = ceiling(x, z);
            if (top < 0) {
                continue;
            }
            int length = 1 + random.nextInt(3);
            if (random.nextBoolean()) {
                if (top - length < FLOOR + 4 || !bareRock(get(x, top + 1, z)) && !get(x, top + 1, z).is(Blocks.DEEPSLATE)) {
                    continue;
                }
                set(x, top + 1, z, Blocks.DRIPSTONE_BLOCK.defaultBlockState());
                for (int k = 0; k < length; k++) {
                    set(x, top - k, z, dripstone(Direction.DOWN, length, k));
                }
            } else if (bareRock(get(x, FLOOR, z)) && top - FLOOR > length + 2) {
                set(x, FLOOR, z, Blocks.DRIPSTONE_BLOCK.defaultBlockState());
                for (int k = 0; k < length; k++) {
                    set(x, FLOOR + 1 + k, z, dripstone(Direction.UP, length, k));
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
