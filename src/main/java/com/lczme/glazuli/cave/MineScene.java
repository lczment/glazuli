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
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
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
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.joml.Vector3f;

/**
 * A lantern-lit grotto round a long pond (title: looking down the water from just above it). A boardwalk runs down the
 * east side, a waterwheel stands in a bay of the west wall, a bridge crosses the far end, and glow berries and leaves
 * hang thick from a low roof with lamps set into it; lily pads, petals and floating lanterns on the water, kelp and
 * coral under it. Round the edge: the player digging out a diamond vein at the end of a mine tunnel (singleplayer),
 * friends at a campfire under a cherry tree (multiplayer), a workshop off the boardwalk (options), an enchanting room
 * behind an amethyst arch (mods) and a cottage with a farm beyond the bridge (everything else).
 */
final class MineScene {
    private static final int SX = 46;
    private static final int SY = 26;
    private static final int SZ = 62;
    /** Top of the ground; the air starts one above. The pond's surface is a block lower. */
    private static final int FLOOR = 6;
    private static final int WATER = FLOOR - 1;
    /** The hall: a rounded rectangle centred here. */
    private static final float CX = 20.5F;
    private static final float CZ = 30.5F;
    private static final float HX = 17.5F;
    private static final float HZ = 29.0F;
    /** The pond runs from just south of the farm to the south wall; its east side lies under the boardwalk. */
    private static final int POND_Z0 = 13;
    private static final int DOCK_X0 = 27;
    private static final int DOCK_X1 = 30;
    private static final int DOCK_Z0 = 14;
    private static final int BRIDGE_Z = 18;
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
            {20.5F, 9.2F, 56.5F, 20.0F, 7.4F, 20.0F},
            {38.4F, 8.6F, 9.3F, 41.8F, 8.1F, 8.5F},
            {28.6F, 9.3F, 36.5F, 34.0F, 7.5F, 30.0F},
            {29.0F, 9.0F, 41.5F, 35.5F, 7.8F, 42.5F},
            {13.0F, 8.8F, 25.5F, 2.5F, 7.8F, 25.5F},
            {19.5F, 10.0F, 21.0F, 19.5F, 7.0F, 7.0F},
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

    private BlockState warmLamp() {
        return (random.nextFloat() < 0.75F ? Blocks.SHROOMLIGHT : Blocks.OCHRE_FROGLIGHT).defaultBlockState();
    }

    // ---------------------------------------------------------------- layout

    private void fill(long seed) {
        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed ^ 0x3A1EL));
        stone(noise);
        hall(noise);
        ground(noise);
        pond();
        banks();
        dock();
        bridge();
        cottage();
        farm();
        enchantingRoom();
        waterwheel();
        mineTunnel();
        camp();
        workshop();
        paths();
        cherry(36, 28, 7, 4.2F);
        cherry(6, 50, 6, 3.8F);
        greenery();
        roof(noise);
        hangingLanterns();
        wallLights();
        wallGreenery();
        glowLichen(noise);
    }

    /** Stone with patches of andesite, tuff and cobble, going to deepslate low down. */
    private void stone(ImprovedNoise noise) {
        for (int z = 0; z < SZ; z++) {
            for (int y = 0; y < SY; y++) {
                for (int x = 0; x < SX; x++) {
                    double n = noise.noise(x * 0.13, y * 0.13, z * 0.13);
                    double m = noise.noise(x * 0.31 + 40.0, y * 0.31, z * 0.31);
                    Block block = y < FLOOR - 1 + n * 3.0 ? m > 0.25 ? Blocks.COBBLED_DEEPSLATE : Blocks.DEEPSLATE
                            : n > 0.33 ? Blocks.ANDESITE : n < -0.33 ? Blocks.TUFF : m > 0.3 ? Blocks.COBBLESTONE : Blocks.STONE;
                    world.set(x, y, z, block.defaultBlockState());
                }
            }
        }
    }

    /** The hall: a long rounded room, its roof highest along the middle and coming down towards the walls. */
    private void hall(ImprovedNoise noise) {
        for (int z = 0; z < SZ; z++) {
            for (int x = 0; x < SX; x++) {
                float u = (x + 0.5F - CX) / HX;
                float v = (z + 0.5F - CZ) / HZ;
                double r = u * u * u * u + v * v * v * v + noise.noise(x * 0.12, 7.0, z * 0.12) * 0.18;
                if (r >= 1.0) {
                    continue;
                }
                int top = FLOOR + 8 + Math.round(5.0F * Mth.sqrt((float) (1.0 - r)) + (float) noise.noise(x * 0.18, 20.0, z * 0.18) * 3.0F);
                for (int y = FLOOR + 1; y <= Math.min(top, SY - 2); y++) {
                    world.set(x, y, z, AIR);
                }
            }
        }
    }

    /** Moss underfoot, a little gravel showing through, moss carpet. */
    private void ground(ImprovedNoise noise) {
        for (int z = 0; z < SZ; z++) {
            for (int x = 0; x < SX; x++) {
                if (!air(x, FLOOR + 1, z)) {
                    continue;
                }
                boolean gravel = noise.noise(x * 0.17, 3.0, z * 0.17) > 0.42;
                set(x, FLOOR, z, (gravel ? Blocks.GRAVEL : Blocks.MOSS_BLOCK).defaultBlockState());
                if (!gravel && random.nextFloat() < 0.12F) {
                    set(x, FLOOR + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
    }

    /** West edge of the pond at {@code z}: wandering, bowing out round the waterwheel, closing in at the north end. */
    private static float westShore(int z) {
        float w = 13.0F + 2.0F * Mth.sin(z * 0.19F) + 1.2F * Mth.sin(z * 0.43F + 2.0F);
        w = Math.min(w, 5.0F + Math.max(0.0F, Math.abs(z - 40) - 2.5F) * 2.0F);
        if (z < POND_Z0 + 3) {
            w += (POND_Z0 + 3 - z) * 2.0F;
        }
        return w;
    }

    /** The pond: clear and fairly deep, kelp, coral and seagrass on a sandy bottom; pads, petals and lanterns afloat. */
    private void pond() {
        Block[][] corals = {
            {Blocks.TUBE_CORAL_BLOCK, Blocks.TUBE_CORAL_FAN}, {Blocks.BUBBLE_CORAL_BLOCK, Blocks.BUBBLE_CORAL},
            {Blocks.BRAIN_CORAL_BLOCK, Blocks.BRAIN_CORAL_FAN}, {Blocks.FIRE_CORAL_BLOCK, Blocks.FIRE_CORAL}};
        for (int z = POND_Z0; z < SZ; z++) {
            float west = westShore(z);
            for (int x = Mth.ceil(west); x <= DOCK_X1; x++) {
                if (!air(x, FLOOR + 2, z)) {
                    continue;
                }
                float edge = Math.min(Math.min(x - west, DOCK_X1 + 1 - x), z - POND_Z0 + 1);
                int depth = edge < 1.5F ? 1 : edge < 3.0F ? 2 : edge < 5.0F ? 3 : 4;
                set(x, FLOOR, z, AIR);
                set(x, FLOOR + 1, z, AIR);
                for (int k = 0; k < depth; k++) {
                    set(x, WATER - k, z, Blocks.WATER.defaultBlockState());
                }
                int bottom = WATER - depth;
                float b = random.nextFloat();
                set(x, bottom, z, (b < 0.45F ? Blocks.SAND : b < 0.75F ? Blocks.CLAY : Blocks.GRAVEL).defaultBlockState());
                float r = random.nextFloat();
                if (r < 0.03F) {
                    Block glow = r < 0.012F ? Blocks.SEA_LANTERN : r < 0.022F ? Blocks.PEARLESCENT_FROGLIGHT : Blocks.VERDANT_FROGLIGHT;
                    set(x, bottom, z, glow.defaultBlockState());
                } else if (depth >= 3 && r < 0.2F) {
                    int top = WATER - random.nextInt(2);
                    for (int y = bottom + 1; y < top; y++) {
                        set(x, y, z, Blocks.KELP_PLANT.defaultBlockState());
                    }
                    set(x, top, z, Blocks.KELP.defaultBlockState());
                } else if (depth >= 2 && r < 0.28F) {
                    Block[] coral = corals[random.nextInt(corals.length)];
                    set(x, bottom, z, coral[0].defaultBlockState());
                    set(x, bottom + 1, z, coral[1].defaultBlockState());
                } else if (depth >= 2 && r < 0.34F) {
                    set(x, bottom + 1, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + random.nextInt(4)));
                } else if (r < 0.62F) {
                    set(x, bottom + 1, z, Blocks.SEAGRASS.defaultBlockState());
                }
                float s = random.nextFloat();
                if (s < 0.07F) {
                    set(x, FLOOR, z, Blocks.LILY_PAD.defaultBlockState());
                } else if (s < 0.095F) {
                    set(x, FLOOR, z, Blocks.PINK_PETALS.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, 1 + random.nextInt(4))
                            .setValue(PinkPetalsBlock.FACING, Direction.from2DDataValue(random.nextInt(4))));
                } else if (s < 0.107F) {
                    set(x, FLOOR, z, Blocks.LANTERN.defaultBlockState());
                } else if (s < 0.125F) {
                    Block[] candles = {Blocks.WHITE_CANDLE, Blocks.PINK_CANDLE, Blocks.LIGHT_BLUE_CANDLE, Blocks.PURPLE_CANDLE, Blocks.MAGENTA_CANDLE};
                    set(x, FLOOR, z, candles[random.nextInt(candles.length)].defaultBlockState()
                            .setValue(CandleBlock.CANDLES, 1 + random.nextInt(3)).setValue(BlockStateProperties.LIT, true));
                }
            }
        }
    }

    /** A rim of mossy stone where the ground meets the water, ferns along it. */
    private void banks() {
        for (int z = 1; z < SZ - 1; z++) {
            for (int x = 1; x < SX - 1; x++) {
                if (!air(x, FLOOR + 2, z) || air(x, FLOOR, z) || get(x, WATER, z).is(Blocks.WATER)) {
                    continue;
                }
                boolean shore = false;
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    shore |= get(x + side.getStepX(), WATER, z + side.getStepZ()).is(Blocks.WATER);
                }
                if (!shore) {
                    continue;
                }
                float r = random.nextFloat();
                set(x, FLOOR, z, (r < 0.45F ? Blocks.MOSSY_COBBLESTONE : r < 0.7F ? Blocks.STONE : Blocks.MOSS_BLOCK).defaultBlockState());
                set(x, FLOOR + 1, z, random.nextFloat() < 0.18F ? Blocks.FERN.defaultBlockState() : AIR);
                set(x, WATER, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            }
        }
    }

    private boolean railAt(int z) {
        return z >= DOCK_Z0 && Math.abs(z - BRIDGE_Z) > 2 && z % 11 != 0 && air(DOCK_X0, FLOOR + 1, z) && z < SZ - 1
                && air(DOCK_X0, FLOOR + 2, z);
    }

    /** The boardwalk down the east side of the pond, on piles, a railing on the water side with gaps to step down. */
    private void dock() {
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState edge = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z);
        BlockState pile = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
        for (int z = DOCK_Z0; z < SZ; z++) {
            for (int x = DOCK_X0; x <= DOCK_X1; x++) {
                if (!air(x, FLOOR + 1, z) && !get(x, FLOOR + 1, z).is(Blocks.MOSS_CARPET)) {
                    continue;
                }
                set(x, FLOOR, z, x == DOCK_X0 ? edge : planks);
                set(x, FLOOR + 1, z, AIR);
                if (z % 5 == 0 && (x == DOCK_X0 || x == DOCK_X1)) {
                    for (int y = WATER; y > 0 && (get(x, y, z).is(Blocks.WATER) || y == WATER); y--) {
                        set(x, y, z, pile);
                    }
                }
            }
        }
        boolean[] rail = new boolean[SZ + 1];
        for (int z = 0; z < SZ; z++) {
            rail[z] = railAt(z);
        }
        for (int z = DOCK_Z0; z < SZ; z++) {
            if (rail[z]) {
                set(DOCK_X0, FLOOR + 1, z, fence(rail[z - 1], false, rail[z + 1], false));
            }
        }
        for (int z : new int[]{52}) {
            if (rail[z]) {
                set(DOCK_X0, FLOOR + 2, z, Blocks.LANTERN.defaultBlockState());
            }
        }
        set(31, FLOOR + 1, 50, Blocks.BARREL.defaultBlockState());
        set(31, FLOOR + 1, 51, Blocks.BARREL.defaultBlockState());
        set(31, FLOOR + 2, 50, Blocks.BARREL.defaultBlockState());
        set(31, FLOOR + 2, 51, Blocks.LANTERN.defaultBlockState());
        set(31, FLOOR + 1, 53, Blocks.POTTED_RED_TULIP.defaultBlockState());
        set(31, FLOOR + 1, 54, Blocks.POTTED_AZURE_BLUET.defaultBlockState());
        set(31, FLOOR + 1, 48, Blocks.LANTERN.defaultBlockState());
        set(31, FLOOR + 1, 35, Blocks.LANTERN.defaultBlockState());
        set(31, FLOOR + 1, 22, Blocks.LANTERN.defaultBlockState());
        set(32, FLOOR + 1, 21, Blocks.HAY_BLOCK.defaultBlockState());
        set(32, FLOOR + 1, 20, Blocks.BARREL.defaultBlockState());
    }

    /** A footbridge over the north end of the pond from the west bank to the boardwalk. */
    private void bridge() {
        int x0 = Math.round(westShore(BRIDGE_Z)) - 2;
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState edge = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X);
        for (int x = x0; x < DOCK_X0; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(x, FLOOR, BRIDGE_Z + dz, dz == 0 ? planks : edge);
                set(x, FLOOR + 1, BRIDGE_Z + dz, AIR);
                if (dz != 0) {
                    set(x, FLOOR + 1, BRIDGE_Z + dz, fence(false, x < DOCK_X0 - 1, false, x > x0));
                    if ((x - x0) % 4 == 2) {
                        for (int y = WATER; y > 0 && get(x, y, BRIDGE_Z + dz).is(Blocks.WATER); y--) {
                            set(x, y, BRIDGE_Z + dz, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
                        }
                    }
                }
            }
        }
        set(x0, FLOOR + 2, BRIDGE_Z - 1, Blocks.LANTERN.defaultBlockState());
        set(x0, FLOOR + 2, BRIDGE_Z + 1, Blocks.LANTERN.defaultBlockState());
    }

    /**
     * A cottage dug into the north-west corner facing the farm: cobble footing, spruce walls framed in stripped logs,
     * lit windows, a stone-brick gable roof with an overhang, a brick chimney and lanterns under the eaves.
     */
    private void cottage() {
        int x0 = 5;
        int x1 = 11;
        int z0 = 4;
        int z1 = 11;
        int ridge = 8;
        int door = 8;
        carve(x0 - 1, FLOOR + 1, z0 - 1, x1 + 2, FLOOR + 11, z1 + 1);
        box(x0 - 1, FLOOR, z0 - 1, x1 + 2, FLOOR, z1 + 1, Blocks.MOSS_BLOCK.defaultBlockState());
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
                    set(x, y, z, corner || z == door && x == x1 ? post : planks);
                }
            }
        }
        for (int z : new int[]{z0 + 1, z0 + 2, z1 - 2, z1 - 1}) {
            set(x1, FLOOR + 2, z, pane(Direction.Axis.Z));
            set(x1, FLOOR + 3, z, pane(Direction.Axis.Z));
            set(x0, FLOOR + 3, z, pane(Direction.Axis.Z));
        }
        set(ridge, FLOOR + 3, z1, pane(Direction.Axis.X));
        set(ridge, FLOOR + 3, z0, pane(Direction.Axis.X));
        BlockState doorState = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST);
        set(x1, FLOOR + 1, door, doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(x1, FLOOR + 2, door, doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        set(x1, FLOOR + 3, door, post);
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
            set(x0 + 1, y, z1 - 1, Blocks.BRICKS.defaultBlockState());
        }
        set(x0 + 1, eaves + 6, z1 - 1, Blocks.CAMPFIRE.defaultBlockState());
        set(x1 + 1, eaves - 1, z0 + 2, hangingLantern());
        set(x1 + 1, eaves - 1, z1 - 2, hangingLantern());
        for (int z : new int[]{z0 + 1, z0 + 2, z1 - 2, z1 - 1}) {
            set(x1 + 1, FLOOR + 1, z, Blocks.MOSS_BLOCK.defaultBlockState());
            Block flower = z % 3 == 0 ? Blocks.CORNFLOWER : z % 3 == 1 ? Blocks.POPPY : Blocks.AZURE_BLUET;
            set(x1 + 1, FLOOR + 2, z, flower.defaultBlockState());
        }
        set(x1 - 1, FLOOR + 1, z0 + 2, Blocks.BARREL.defaultBlockState());
        set(x1 - 1, FLOOR + 2, z0 + 2, Blocks.LANTERN.defaultBlockState());
        set(x1 - 1, FLOOR + 1, z1 - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(x1 - 1, FLOOR + 2, z1 - 2, Blocks.LANTERN.defaultBlockState());
        set(x0 + 1, FLOOR + 1, z0 + 2, facing(Blocks.FURNACE, Direction.EAST).setValue(BlockStateProperties.LIT, true));
        box(x0 + 1, FLOOR + 1, z0 + 3, x0 + 1, FLOOR + 2, z0 + 4, Blocks.BOOKSHELF.defaultBlockState());
        set(x1 + 2, FLOOR + 1, z0, Blocks.BARREL.defaultBlockState());
        set(x1 + 2, FLOOR + 2, z0, Blocks.BARREL.defaultBlockState());
        set(x1 + 2, FLOOR + 1, z0 + 1, Blocks.BARREL.defaultBlockState());
    }

    /** Crop rows beyond the bridge with a channel down the middle, fenced, a farmer hoeing. */
    private void farm() {
        int x0 = 15;
        int x1 = 25;
        int z0 = 4;
        int z1 = 10;
        carve(x0 - 1, FLOOR + 1, z0 - 1, x1 + 2, FLOOR + 4, z1 + 1);
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        CropBlock[] rows = {wheat, (CropBlock) Blocks.CARROTS, (CropBlock) Blocks.POTATOES, null, (CropBlock) Blocks.BEETROOTS,
            wheat, (CropBlock) Blocks.CARROTS};
        BlockState soil = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
        for (int z = z0; z <= z1; z++) {
            CropBlock crop = rows[z - z0];
            for (int x = x0; x <= x1; x++) {
                if (crop == null) {
                    set(x, FLOOR, z, Blocks.WATER.defaultBlockState());
                    set(x, FLOOR + 1, z, AIR);
                    continue;
                }
                set(x, FLOOR, z, soil);
                set(x, FLOOR + 1, z, crop.getStateForAge(Math.max(0, crop.getMaxAge() - random.nextInt(3))));
            }
        }
        BlockState alongX = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X);
        BlockState alongZ = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z);
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            set(x, FLOOR, z1 + 1, alongX);
            boolean gate = x == 19 || x == 20;
            if (!gate) {
                set(x, FLOOR + 1, z1 + 1, fence(false, x < x1 + 1 && x != 18, false, x > x0 - 1 && x != 21));
            }
        }
        for (int z = z0; z <= z1; z++) {
            set(x0 - 1, FLOOR, z, alongZ);
            set(x1 + 1, FLOOR, z, alongZ);
            set(x0 - 1, FLOOR + 1, z, fence(z > z0, false, true, false));
            set(x1 + 1, FLOOR + 1, z, fence(z > z0, false, true, false));
        }
        set(x0 - 1, FLOOR + 1, z1 + 1, fence(true, true, false, false));
        set(x1 + 1, FLOOR + 1, z1 + 1, fence(true, false, false, true));
        set(x0 - 1, FLOOR + 2, z1 + 1, Blocks.LANTERN.defaultBlockState());
        set(x1 + 1, FLOOR + 2, z1 + 1, Blocks.LANTERN.defaultBlockState());
        set(x1 + 2, FLOOR + 1, z0 + 1, Blocks.HAY_BLOCK.defaultBlockState());
        set(x1 + 2, FLOOR + 1, z0 + 2, Blocks.HAY_BLOCK.defaultBlockState());
        set(x1 + 2, FLOOR + 2, z0 + 1, Blocks.HAY_BLOCK.defaultBlockState());
        set(x1 + 2, FLOOR + 1, z0 + 4, Blocks.COMPOSTER.defaultBlockState());
        set(x1 + 2, FLOOR + 1, z0 + 5, Blocks.BARREL.defaultBlockState());
        set(x1 + 2, FLOOR + 2, z0 + 5, Blocks.LANTERN.defaultBlockState());
        set(19, FLOOR + 1, 9, AIR);
        world.figures.add(new CaveWorld.Figure(19.5F, FLOOR + 0.9375F, 9.5F, 20.0F, false, 5,
                new ItemStack(Items.IRON_HOE), ItemStack.EMPTY, true));
    }

    /** In the west wall: an enchanting table ringed by bookshelves and candles behind an amethyst-studded arch. */
    private void enchantingRoom() {
        int x0 = 0;
        int x1 = 6;
        int z0 = 22;
        int z1 = 28;
        int mid = 25;
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                    float r = random.nextFloat();
                    set(x, y, z, !wall ? AIR : r < 0.25F ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                            : r < 0.4F ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : bricks);
                }
            }
        }
        box(x0, FLOOR + 6, z0, x1, FLOOR + 6, z1, Blocks.DEEPSLATE_TILES.defaultBlockState());
        box(x0 + 1, FLOOR, z0 + 1, x1 - 1, FLOOR, z1 - 1, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        carve(x1, FLOOR + 1, mid - 1, x1, FLOOR + 3, mid + 1);
        set(x1, FLOOR + 3, mid - 1, stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, true));
        set(x1, FLOOR + 3, mid + 1, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, true));
        set(2, FLOOR + 1, mid, Blocks.ENCHANTING_TABLE.defaultBlockState());
        blockEntity(2, FLOOR + 1, mid, "minecraft:enchanting_table");
        box(x0 + 1, FLOOR + 1, z0 + 1, x0 + 1, FLOOR + 2, z1 - 1, Blocks.BOOKSHELF.defaultBlockState());
        box(x0 + 2, FLOOR + 1, z0 + 1, x1 - 2, FLOOR + 2, z0 + 1, Blocks.BOOKSHELF.defaultBlockState());
        box(x0 + 2, FLOOR + 1, z1 - 1, x1 - 2, FLOOR + 2, z1 - 1, Blocks.BOOKSHELF.defaultBlockState());
        for (int[] c : new int[][]{{1, 23}, {1, 26}, {3, 23}, {4, 27}, {1, 25}}) {
            set(c[0], FLOOR + 3, c[1], Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(3))
                    .setValue(BlockStateProperties.LIT, true));
        }
        set(3, FLOOR + 3, 27, Blocks.LANTERN.defaultBlockState());
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            for (int y = FLOOR + 1; y <= FLOOR + 6; y++) {
                boolean arch = Math.abs(z - mid) <= 1 && y <= FLOOR + 3;
                if (arch || random.nextFloat() > 0.35F || air(x1, y, z)) {
                    continue;
                }
                set(x1, y, z, random.nextFloat() < 0.2F ? Blocks.BUDDING_AMETHYST.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState());
                if (air(x1 + 1, y, z) && random.nextFloat() < 0.6F) {
                    set(x1 + 1, y, z, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.EAST));
                }
            }
        }
    }

    /**
     * A stone-brick mill against the west wall with lit windows, and on its south side a waterwheel standing in a bay
     * of the pond, its face towards the far end of the hall.
     */
    private void waterwheel() {
        int x0 = 3;
        int x1 = 11;
        int z0 = 30;
        int z1 = 37;
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                for (int y = 1; y <= FLOOR + 7; y++) {
                    float r = random.nextFloat();
                    set(x, y, z, (r < 0.3F ? Blocks.MOSSY_STONE_BRICKS : r < 0.4F ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState());
                }
                boolean edge = x == x1 || z == z0 || z == z1;
                set(x, FLOOR + 8, z, edge ? slab(Blocks.STONE_BRICK_SLAB, SlabType.BOTTOM) : Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int z : new int[]{z0 + 2, z0 + 5}) {
            set(x1, FLOOR + 3, z, pane(Direction.Axis.Z));
            set(x1, FLOOR + 4, z, pane(Direction.Axis.Z));
            set(x1 - 1, FLOOR + 3, z, Blocks.SHROOMLIGHT.defaultBlockState());
            set(x1 - 1, FLOOR + 4, z, Blocks.SHROOMLIGHT.defaultBlockState());
        }
        for (int x : new int[]{x0 + 2, x1 - 1}) {
            set(x, FLOOR + 3, z1, pane(Direction.Axis.X));
            set(x, FLOOR + 3, z1 - 1, Blocks.SHROOMLIGHT.defaultBlockState());
        }
        set(x0 + 1, FLOOR + 6, z1, Blocks.SHROOMLIGHT.defaultBlockState());
        set(x1, FLOOR + 6, z1, Blocks.SHROOMLIGHT.defaultBlockState());
        set(x1 + 1, FLOOR + 5, z0 + 1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
        int cx = 8;
        int cy = FLOOR + 1;
        BlockState rim = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        for (int dy = -5; dy <= 5; dy++) {
            for (int dx = -5; dx <= 5; dx++) {
                float d = Mth.sqrt(dx * dx + dy * dy);
                if (d >= 3.5F && d < 4.5F) {
                    set(cx + dx, cy + dy, z1 + 1, rim);
                    set(cx + dx, cy + dy, z1 + 3, rim);
                } else if (d >= 1.0F && d < 3.5F && (dx == 0 || dy == 0)) {
                    BlockState spoke = log(Blocks.STRIPPED_DARK_OAK_LOG, dy == 0 ? Direction.Axis.X : Direction.Axis.Y);
                    set(cx + dx, cy + dy, z1 + 1, spoke);
                    set(cx + dx, cy + dy, z1 + 3, spoke);
                }
            }
        }
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            int px = cx + Math.round(4.8F * Mth.cos(a));
            int py = cy + Math.round(4.8F * Mth.sin(a));
            for (int z = z1 + 1; z <= z1 + 3; z++) {
                set(px, py, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
        for (int z = z1; z <= z1 + 4; z++) {
            set(cx, cy, z, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
        }
    }

    /** A timbered mine tunnel into the east wall, ending at a small diamond vein the player is digging out. */
    private void mineTunnel() {
        int x0 = 35;
        int x1 = 43;
        int z0 = 6;
        int z1 = 10;
        int mid = 8;
        for (int x = x0; x <= x1 + 1; x++) {
            for (int y = FLOOR; y <= FLOOR + 5; y++) {
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    boolean inside = x <= x1 && y > FLOOR && y < FLOOR + 5 && z >= z0 && z <= z1;
                    set(x, y, z, inside ? AIR : random.nextFloat() < 0.08F && y > FLOOR ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState()
                            : (y == FLOOR ? Blocks.COBBLED_DEEPSLATE : Blocks.DEEPSLATE).defaultBlockState());
                }
            }
        }
        BlockState diamond = Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
        set(x1 + 1, FLOOR + 1, mid, diamond);
        set(x1 + 1, FLOOR + 2, mid, diamond);
        set(x1 + 1, FLOOR + 2, mid + 1, diamond);
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int x : new int[]{x0 + 1, x0 + 3}) {
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                set(x, y, z0, post);
                set(x, y, z1, post);
            }
            for (int z = z0; z <= z1; z++) {
                set(x, FLOOR + 4, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
            }
        }
        set(x0 + 5, FLOOR + 3, z0, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        set(x1 - 1, FLOOR + 3, z1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
        set(x0 + 2, FLOOR + 1, z1, Blocks.LANTERN.defaultBlockState());
        set(x1, FLOOR + 1, z0, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(x1, FLOOR + 1, z1, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(x1, FLOOR + 2, z1, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        for (int x = x0 - 3; x <= x1 - 3; x++) {
            if (air(x, FLOOR + 1, mid)) {
                set(x, FLOOR + 1, mid, Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, RailShape.EAST_WEST));
            }
        }
        world.figures.add(new CaveWorld.Figure(41.6F, FLOOR + 1, mid + 0.5F, -90.0F, true, 0,
                new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.TORCH), true));
    }

    /** East of the boardwalk under the cherry tree: friends round a campfire on stair benches, supplies behind. */
    private void camp() {
        int fx = 33;
        int fz = 30;
        set(fx, FLOOR + 1, fz, Blocks.CAMPFIRE.defaultBlockState());
        set(fx, FLOOR, fz, Blocks.COBBLESTONE.defaultBlockState());
        set(fx - 2, FLOOR + 1, fz - 1, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        set(fx + 2, FLOOR + 1, fz + 2, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        set(fx + 3, FLOOR + 1, fz + 3, Blocks.BARREL.defaultBlockState());
        set(fx + 3, FLOOR + 1, fz + 4, Blocks.BARREL.defaultBlockState());
        set(fx + 3, FLOOR + 2, fz + 3, Blocks.LANTERN.defaultBlockState());
        float[][] friends = {{31.7F, 30.0F}, {33.2F, 32.4F}, {35.0F, 29.2F}};
        ItemStack[] hands = {new ItemStack(Items.TORCH), new ItemStack(Items.BREAD), new ItemStack(Items.IRON_SHOVEL)};
        int[] skins = {2, 9, 14};
        for (int i = 0; i < friends.length; i++) {
            float yaw = (float) Math.toDegrees(Mth.atan2(-(fx + 0.5F - friends[i][0]), fz + 0.5F - friends[i][1]));
            world.figures.add(new CaveWorld.Figure(friends[i][0], FLOOR + 1, friends[i][1], yaw, false, skins[i], hands[i],
                    ItemStack.EMPTY, false));
        }
    }

    /** Off the boardwalk, dug into the east wall: furnaces, anvil, smithing table, books and a smith at work. */
    private void workshop() {
        int x0 = 31;
        int x1 = 39;
        int z0 = 38;
        int z1 = 46;
        carve(x0, FLOOR + 1, z0, x1, FLOOR + 5, z1);
        box(x0, FLOOR, z0, x1, FLOOR, z1, Blocks.SPRUCE_PLANKS.defaultBlockState());
        set(x1, FLOOR + 1, z0 + 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(x1, FLOOR + 1, z0 + 2, facing(Blocks.FURNACE, Direction.WEST).setValue(BlockStateProperties.LIT, true));
        set(x1, FLOOR + 1, z0 + 3, facing(Blocks.BLAST_FURNACE, Direction.WEST).setValue(BlockStateProperties.LIT, true));
        set(x1, FLOOR + 1, z0 + 4, Blocks.SMITHING_TABLE.defaultBlockState());
        set(x1, FLOOR + 2, z0 + 2, Blocks.LANTERN.defaultBlockState());
        box(x1, FLOOR + 1, z0 + 5, x1, FLOOR + 2, z1 - 1, Blocks.BOOKSHELF.defaultBlockState());
        set(x1 - 1, FLOOR + 1, z1 - 1, facing(Blocks.LECTERN, Direction.WEST));
        set(34, FLOOR + 1, 44, facing(Blocks.ANVIL, Direction.EAST));
        set(36, FLOOR + 1, z1, Blocks.GRINDSTONE.defaultBlockState());
        set(32, FLOOR + 1, z0, Blocks.BARREL.defaultBlockState());
        set(33, FLOOR + 1, z0, Blocks.BARREL.defaultBlockState());
        set(32, FLOOR + 2, z0, Blocks.BARREL.defaultBlockState());
        set(36, FLOOR + 1, z0, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        blockEntity(36, FLOOR + 1, z0, "minecraft:chest");
        BlockState post = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
        for (int z : new int[]{z0, z1}) {
            for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
                set(x0, y, z, post);
            }
        }
        for (int z = z0; z <= z1; z++) {
            set(x0, FLOOR + 5, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
            set(x0 - 1, FLOOR + 5, z, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
        }
        set(35, FLOOR + 4, 42, hangingLantern());
        set(35, FLOOR + 5, 42, Blocks.CHAIN.defaultBlockState());
        world.figures.add(new CaveWorld.Figure(35.5F, FLOOR + 1, 42.5F, 45.0F, false, 11,
                new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY, true));
    }

    /** Stone paths round the north end and down the west bank, three wide, a lamp set into the ground every few steps. */
    private void paths() {
        int[][] legs = {{10, 20, 10, 29}, {10, 20, 12, 13}, {12, 8, 12, 13}, {12, 12, 30, 12}, {30, 12, 35, 8},
            {10, 46, 10, 56}};
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
        BlockState above = get(x, FLOOR + 1, z);
        if (!(under.is(Blocks.MOSS_BLOCK) || under.is(Blocks.GRAVEL) || under.is(Blocks.STONE) || under.is(Blocks.MOSSY_COBBLESTONE))
                || !(above.isAir() || above.is(Blocks.MOSS_CARPET) || above.is(Blocks.FERN))) {
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
        for (int i = 0; i < 14; i++) {
            int px = x + random.nextInt(9) - 4;
            int pz = z + random.nextInt(9) - 4;
            if (air(px, FLOOR + 1, pz) && get(px, FLOOR, pz).is(Blocks.MOSS_BLOCK)) {
                set(px, FLOOR + 1, pz, Blocks.PINK_PETALS.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, 1 + random.nextInt(4)));
            }
        }
    }

    /** Azalea bushes, ferns and grass over the open moss, kept clear of where people stand. */
    private void greenery() {
        for (int z = 1; z < SZ - 1; z++) {
            for (int x = 1; x < SX - 1; x++) {
                if (!get(x, FLOOR, z).is(Blocks.MOSS_BLOCK) || !(air(x, FLOOR + 1, z) || get(x, FLOOR + 1, z).is(Blocks.MOSS_CARPET))) {
                    continue;
                }
                boolean nearFigure = false;
                for (CaveWorld.Figure figure : world.figures) {
                    nearFigure |= Math.abs(figure.x() - x - 0.5F) < 2.0F && Math.abs(figure.z() - z - 0.5F) < 2.0F;
                }
                if (nearFigure) {
                    continue;
                }
                float r = random.nextFloat();
                Block plant = r < 0.03F ? Blocks.AZALEA : r < 0.045F ? Blocks.FLOWERING_AZALEA : r < 0.1F ? Blocks.FERN
                        : r < 0.19F ? Blocks.SHORT_GRASS : r < 0.196F ? Blocks.LANTERN : null;
                if (plant != null) {
                    set(x, FLOOR + 1, z, plant.defaultBlockState());
                }
            }
        }
    }

    /**
     * The roof: moss in patches, lamps set flush into the rock, glow berries hanging thick (never below head height
     * over the walkways), clumps of azalea leaves and spore blossoms.
     */
    private void roof(ImprovedNoise noise) {
        for (int z = 0; z < SZ; z++) {
            for (int x = 0; x < SX; x++) {
                int top = ceiling(x, FLOOR + 1, z);
                if (top < FLOOR + 7) {
                    continue;
                }
                int y = top + 1;
                if (!rock(get(x, y, z))) {
                    continue;
                }
                if (noise.noise(x * 0.2, 50.0, z * 0.2) > 0.15) {
                    set(x, y, z, Blocks.MOSS_BLOCK.defaultBlockState());
                }
                float r = random.nextFloat();
                if (r < 0.02F) {
                    set(x, y, z, warmLamp());
                } else if (r < 0.15F) {
                    int longest = Math.min(6, top - (FLOOR + 6) + 1);
                    int length = 1 + random.nextInt(longest);
                    for (int k = 0; k < length; k++) {
                        Block block = k == length - 1 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT;
                        set(x, top - k, z, block.defaultBlockState().setValue(CaveVines.BERRIES, random.nextFloat() < 0.55F));
                    }
                } else if (r < 0.175F) {
                    Block leaf = random.nextFloat() < 0.4F ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES;
                    set(x, top, z, leaves(leaf));
                    if (random.nextFloat() < 0.5F && top - 1 > FLOOR + 6) {
                        set(x, top - 1, z, leaves(leaf));
                    }
                } else if (r < 0.185F) {
                    set(x, top, z, Blocks.SPORE_BLOSSOM.defaultBlockState());
                } else if (r < 0.195F) {
                    set(x, y, z, Blocks.AMETHYST_BLOCK.defaultBlockState());
                    Block crystal = random.nextFloat() < 0.6F ? Blocks.AMETHYST_CLUSTER : Blocks.LARGE_AMETHYST_BUD;
                    set(x, top, z, crystal.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN));
                }
            }
        }
    }

    /** Lanterns, soul and ordinary, hung on chains from the roof over the pond, all well above head height. */
    private void hangingLanterns() {
        for (int placed = 0, tries = 0; placed < 14 && tries < 400; tries++) {
            int z = 20 + random.nextInt(SZ - 24);
            int x = Mth.ceil(westShore(z)) + random.nextInt(Math.max(1, DOCK_X1 - Mth.ceil(westShore(z))));
            int top = ceiling(x, FLOOR + 1, z);
            int lantern = FLOOR + 7 + random.nextInt(2);
            if (top < lantern + 2 || !get(x, WATER, z).is(Blocks.WATER)) {
                continue;
            }
            for (int y = lantern + 1; y <= top; y++) {
                set(x, y, z, Blocks.CHAIN.defaultBlockState());
            }
            Block block = random.nextFloat() < 0.5F ? Blocks.SOUL_LANTERN : Blocks.LANTERN;
            set(x, lantern, z, block.defaultBlockState().setValue(LanternBlock.HANGING, true));
            placed++;
        }
    }

    /** Patches of glow lichen over the rock, a soft green light up the walls and across the roof. */
    private void glowLichen(ImprovedNoise noise) {
        for (int z = 1; z < SZ - 1; z++) {
            for (int y = FLOOR + 2; y < SY - 1; y++) {
                for (int x = 1; x < SX - 1; x++) {
                    if (!air(x, y, z) || noise.noise(x * 0.15, y * 0.15 + 90.0, z * 0.15) < 0.22 || random.nextFloat() > 0.45F) {
                        continue;
                    }
                    for (Direction side : Direction.values()) {
                        if (side != Direction.DOWN && rock(get(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ()))) {
                            set(x, y, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(side), true));
                            break;
                        }
                    }
                }
            }
        }
    }

    /** Warm lamps set flush into the walls here and there. */
    private void wallLights() {
        for (int z = 0; z < SZ; z++) {
            for (int y = FLOOR + 3; y < SY - 2; y++) {
                for (int x = 0; x < SX; x++) {
                    if (!rock(get(x, y, z)) || random.nextFloat() > 0.012F) {
                        continue;
                    }
                    if (air(x + 1, y, z) || air(x - 1, y, z) || air(x, y, z + 1) || air(x, y, z - 1)) {
                        set(x, y, z, warmLamp());
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
                        } else if (y > FLOOR + 5 && random.nextFloat() < 0.04F) {
                            BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(side.getOpposite()), true);
                            int length = 2 + random.nextInt(6);
                            for (int k = 0; k < length && air(ax, y - k, az) && y - k > FLOOR + 2; k++) {
                                set(ax, y - k, az, vine);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }
}
