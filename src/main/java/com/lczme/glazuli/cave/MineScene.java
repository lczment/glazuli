package com.lczme.glazuli.cave;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
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
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
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
 * coral under it, candles on the railings and the stones, drifts of flowers on the moss; a cottage and a farm beyond
 * the bridge, an enchanting room behind an amethyst arch. The menus' views all stand close round the title view, so
 * the camera never has far to go: the player digging out a diamond vein in an amethyst pocket at the back of a shallow
 * mine cut in the west wall (singleplayer), friends at a campfire under a cherry tree in a bay of the east wall
 * (multiplayer), a workshop off the boardwalk (options), foxes asleep in a den beside the mine cut (mods) and the
 * candles afloat on the pond (everything else).
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
    /** The mine cut: its mouth in the west wall, dug west, as wide as these. */
    private static final int MINE_X = 7;
    private static final int MINE_DIR = -1;
    private static final int MINE_Z0 = 49;
    private static final int MINE_Z1 = 55;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Block[] CANDLES = {Blocks.WHITE_CANDLE, Blocks.PINK_CANDLE, Blocks.LIGHT_BLUE_CANDLE, Blocks.PURPLE_CANDLE,
        Blocks.MAGENTA_CANDLE};

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
            {12.3F, 9.3F, 51.8F, 4.5F, 8.0F, 52.3F},
            {28.5F, 9.4F, 57.0F, 35.5F, 7.6F, 51.5F},
            {28.8F, 9.4F, 45.2F, 36.0F, 7.8F, 41.5F},
            {9.6F, 8.9F, 46.1F, 3.8F, 6.9F, 45.3F},
            {21.0F, 8.2F, 50.5F, 18.5F, 5.8F, 43.0F},
        };
        Vector3f[] eyes = new Vector3f[shots.length];
        Vector3f[] looks = new Vector3f[shots.length];
        for (int i = 0; i < shots.length; i++) {
            eyes[i] = new Vector3f(shots[i][0], shots[i][1], shots[i][2]);
            looks[i] = new Vector3f(shots[i][3], shots[i][4], shots[i][5]);
        }
        // Startup: from low over the middle of the pond, looking up into the lanterns, drawing back and up along the
        // water while the view settles down onto the title.
        CustomRig rig = new CustomRig(eyes, looks).intro(2.5F,
                new Vector3f(19.5F, 12.0F, 21.0F),
                new Vector3f(23.0F, 7.6F, 31.5F),
                new Vector3f(22.2F, 8.6F, 43.0F));
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

    /** An open spruce trapdoor standing against the side of its block opposite {@code facing}. */
    private static BlockState shutter(Direction facing) {
        return Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.OPEN, true);
    }

    private static BlockState leaves(Block block) {
        return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    private static BlockState candle(Block block, int count) {
        return block.defaultBlockState().setValue(CandleBlock.CANDLES, count).setValue(BlockStateProperties.LIT, true);
    }

    private boolean nearFigure(int x, int z) {
        for (CaveWorld.Figure figure : world.figures) {
            if (Math.abs(figure.x() - x - 0.5F) < 2.0F && Math.abs(figure.z() - z - 0.5F) < 2.0F) {
                return true;
            }
        }
        return false;
    }

    /** In front of the enchanting room's arch, which the plants leave clear. */
    private static boolean doorway(int x, int z) {
        return x >= 7 && x <= 8 && z >= 24 && z <= 26;
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
        cherry(38, 49, 6, 3.6F);
        canopyLantern(34, 28);
        canopyLantern(38, 49);
        greenery();
        roof(noise);
        hangingLanterns();
        wallLights();
        wallGreenery();
        glowLichen(noise);
        railCandles();
        stoneCandles();
        flowers(noise);
        sporeBlossoms();
        geode();
        foxDen();
        rockWindows();
    }

    /**
     * A low mossy den dug into the west wall beside the mine cut, flowering azalea over its mouth and berry bushes
     * before it: two foxes and a cub curled up asleep in and in front of it (NeoForge's fox, for the mods menu).
     */
    private void foxDen() {
        float cx = 3.0F;
        float cz = 45.5F;
        for (int z = 43; z <= 47; z++) {
            for (int x = 0; x <= 5; x++) {
                float u = (x + 0.5F - cx) / 3.0F;
                float v = (z + 0.5F - cz) / 2.6F;
                float r = u * u + v * v;
                if (r >= 1.0F) {
                    continue;
                }
                for (int y = FLOOR + 1; y <= FLOOR + (r < 0.45F ? 3 : 2); y++) {
                    set(x, y, z, AIR);
                }
                set(x, FLOOR, z, Blocks.MOSS_BLOCK.defaultBlockState());
                if (x < 2 && z != 45) {
                    set(x, FLOOR + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
        BlockState azalea = leaves(Blocks.FLOWERING_AZALEA_LEAVES);
        for (int z = 43; z <= 47; z++) {
            for (int x = 4; x <= 6; x++) {
                int y = FLOOR + (x == 6 ? 4 : 3);
                if (air(x, y, z) && (z != 43 && z != 47 || x < 6)) {
                    set(x, y, z, azalea);
                }
            }
        }
        set(1, FLOOR + 1, 45, candle(Blocks.WHITE_CANDLE, 3));
        set(5, FLOOR + 1, 43, Blocks.LANTERN.defaultBlockState());
        for (int[] bush : new int[][]{{6, 44}, {7, 47}}) {
            if (standable(bush[0], bush[1])) {
                set(bush[0], FLOOR + 1, bush[1], Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
            }
        }
        float[][] foxes = {{2.6F, 45.8F, 70.0F}, {3.7F, 44.4F, -40.0F}, {6.6F, 46.1F, -110.0F}};
        for (int i = 0; i < foxes.length; i++) {
            int bx = Mth.floor(foxes[i][0]);
            int bz = Mth.floor(foxes[i][1]);
            for (int z = bz - 2; z <= bz + 2; z++) {
                for (int x = bx - 2; x <= bx + 2; x++) {
                    if (get(x, FLOOR + 1, z).getBlock() instanceof FlowerBlock) {
                        set(x, FLOOR + 1, z, AIR);
                    }
                }
            }
            set(bx, FLOOR + 1, bz, AIR);
            if (get(bx, FLOOR, bz).isAir() || !get(bx, FLOOR, bz).getFluidState().isEmpty()) {
                set(bx, FLOOR, bz, Blocks.MOSS_BLOCK.defaultBlockState());
            }
            world.foxes.add(new CaveWorld.SleepingFox(foxes[i][0], FLOOR + 1, foxes[i][1], foxes[i][2], i == 1));
        }
    }

    /**
     * Lit windows of rooms dug into the rock, up the hall walls where they face open air, timber framed with the
     * shutters open; spread out, never two close together.
     */
    private void rockWindows() {
        int y = FLOOR + 4;
        List<int[]> chosen = new ArrayList<>();
        for (int z = 1; z < SZ - 1; z++) {
            for (int x = 1; x < SX - 1; x++) {
                for (Direction face : Direction.Plane.HORIZONTAL) {
                    if (!windowFits(x, y, z, face) || random.nextFloat() > 0.3F) {
                        continue;
                    }
                    boolean spaced = true;
                    for (int[] c : chosen) {
                        spaced &= Math.abs(c[0] - x) + Math.abs(c[1] - z) >= 12;
                    }
                    if (!spaced) {
                        continue;
                    }
                    chosen.add(new int[]{x, z});
                    Direction side = face.getClockWise();
                    BlockState frame = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
                    BlockState beam = log(Blocks.STRIPPED_SPRUCE_LOG, side.getAxis());
                    for (int dy = 0; dy <= 1; dy++) {
                        set(x, y + dy, z, pane(side.getAxis()));
                        set(x - face.getStepX(), y + dy, z - face.getStepZ(), Blocks.SHROOMLIGHT.defaultBlockState());
                        for (int s = -1; s <= 1; s += 2) {
                            set(x + s * side.getStepX(), y + dy, z + s * side.getStepZ(), frame);
                            set(x + s * side.getStepX() + face.getStepX(), y + dy, z + s * side.getStepZ() + face.getStepZ(), shutter(face));
                        }
                    }
                    for (int s = -1; s <= 1; s++) {
                        set(x + s * side.getStepX(), y + 2, z + s * side.getStepZ(), beam);
                        set(x + s * side.getStepX(), y - 1, z + s * side.getStepZ(), beam);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Whether a window fits in the wall at {@code (x, y, z)} looking out {@code face}: rock round it and behind, the
     * hall open in front, clear of the rooms already built into the walls.
     */
    private boolean windowFits(int x, int y, int z, Direction face) {
        int fx = face.getStepX();
        int fz = face.getStepZ();
        Direction side = face.getClockWise();
        if (x >= 29 && z >= 36 || x <= 13 && (z <= 13 || z >= 20 && z <= 39 || z >= 41)) {
            return false;
        }
        for (int dy = -1; dy <= 2; dy++) {
            for (int s = -1; s <= 1; s++) {
                if (air(x + s * side.getStepX(), y + dy, z + s * side.getStepZ())) {
                    return false;
                }
            }
        }
        for (int dy = 0; dy <= 1; dy++) {
            if (!rock(get(x, y + dy, z)) || !rock(get(x - fx, y + dy, z - fz))) {
                return false;
            }
            for (int s = -1; s <= 1; s++) {
                if (!loose(x + fx + s * side.getStepX(), y + dy, z + fz + s * side.getStepZ())) {
                    return false;
                }
            }
            for (int k = 2; k <= 4; k++) {
                if (!loose(x + k * fx, y + dy, z + k * fz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Air or something growing there that a block can take the place of. */
    private boolean loose(int x, int y, int z) {
        BlockState state = get(x, y, z);
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
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
                    Block glow = r < 0.022F ? Blocks.SEA_LANTERN : Blocks.VERDANT_FROGLIGHT;
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
                    set(x, FLOOR, z, candle(CANDLES[random.nextInt(CANDLES.length)], 1 + random.nextInt(3)));
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
            set(x1 + 2, FLOOR + 1, z, shutter(Direction.EAST));
        }
        set(x1 + 1, FLOOR + 1, z0, shutter(Direction.NORTH));
        set(x1 + 1, FLOOR + 1, z0 + 3, shutter(Direction.SOUTH));
        set(x1 + 1, FLOOR + 1, z1, shutter(Direction.SOUTH));
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
        int crowX = 23;
        int crowZ = z0 + 1;
        set(crowX, FLOOR + 1, crowZ, fence(false, false, false, false));
        set(crowX, FLOOR + 2, crowZ, Blocks.HAY_BLOCK.defaultBlockState());
        set(crowX, FLOOR + 3, crowZ, facing(Blocks.CARVED_PUMPKIN, Direction.SOUTH));
        set(crowX - 1, FLOOR + 2, crowZ, fence(false, true, false, false));
        set(crowX + 1, FLOOR + 2, crowZ, fence(false, false, false, true));
        Block[] border = {Blocks.PUMPKIN, Blocks.SWEET_BERRY_BUSH, Blocks.LILAC, Blocks.PUMPKIN, Blocks.ROSE_BUSH, Blocks.MELON,
            Blocks.SWEET_BERRY_BUSH, Blocks.PEONY, Blocks.PUMPKIN, Blocks.SWEET_BERRY_BUSH, Blocks.LILAC};
        for (int x = x0; x <= x1; x++) {
            int z = z0 - 1;
            if (!air(x, FLOOR + 1, z) || !air(x, FLOOR + 2, z)) {
                continue;
            }
            Block plant = border[(x - x0) % border.length];
            set(x, FLOOR, z, Blocks.MOSS_BLOCK.defaultBlockState());
            if (plant instanceof DoublePlantBlock) {
                set(x, FLOOR + 1, z, plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                set(x, FLOOR + 2, z, plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            } else if (plant == Blocks.SWEET_BERRY_BUSH) {
                set(x, FLOOR + 1, z, plant.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
            } else {
                set(x, FLOOR + 1, z, plant.defaultBlockState());
            }
        }
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
        set(x1, FLOOR + 4, mid, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        for (int z : new int[]{mid - 2, mid + 2}) {
            set(x1 + 1, FLOOR + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
            set(x1 + 1, FLOOR + 2, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
            set(x1 + 1, FLOOR + 3, z, Blocks.SOUL_LANTERN.defaultBlockState());
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
        millUpperStorey(x0, x1, z0, z1);
        set(x1 + 1, FLOOR + 4, z0 + 1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
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

    /**
     * The mill's top storey timbered over the stone (a sill beam, posts every third block, planks between, small lit
     * windows) under an eave of stairs all round.
     */
    private void millUpperStorey(int x0, int x1, int z0, int z1) {
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState post = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                boolean east = x == x1;
                if (!east && z != z0 && z != z1) {
                    continue;
                }
                boolean corner = east && (z == z0 || z == z1);
                boolean upright = corner || (east ? (z - z0) % 3 == 0 : (x - x0) % 3 == 0);
                set(x, FLOOR + 5, z, log(Blocks.STRIPPED_SPRUCE_LOG, east && !corner ? Direction.Axis.Z : Direction.Axis.X));
                for (int y = FLOOR + 6; y <= FLOOR + 7; y++) {
                    set(x, y, z, upright ? post : planks);
                }
            }
        }
        for (int z : new int[]{z0 + 2, z1 - 2}) {
            set(x1, FLOOR + 6, z, pane(Direction.Axis.Z));
            set(x1 - 1, FLOOR + 6, z, Blocks.SHROOMLIGHT.defaultBlockState());
        }
        for (int x : new int[]{x0 + 1, x0 + 4, x0 + 7}) {
            for (int z : new int[]{z0, z1}) {
                set(x, FLOOR + 6, z, pane(Direction.Axis.X));
                set(x, FLOOR + 6, z == z0 ? z + 1 : z - 1, Blocks.SHROOMLIGHT.defaultBlockState());
            }
        }
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            set(x1 + 1, FLOOR + 8, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        }
        for (int x = x0; x <= x1; x++) {
            set(x, FLOOR + 8, z0 - 1, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
            set(x, FLOOR + 8, z1 + 1, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        }
    }

    /** A shallow, wide-open cut in the west wall, the player digging out a small diamond vein at the back of it. */
    private void mineTunnel() {
        int z0 = MINE_Z0;
        int z1 = MINE_Z1;
        int mid = (z0 + z1) / 2;
        int top = FLOOR + 5;
        // Columns counted into the cut: open from the mouth (0) to 4, the face being dug at 5.
        int near = mineX(1);
        int frame = mineX(2);
        int far = mineX(4);
        int face = mineX(5);
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        for (int d = 0; d <= 5; d++) {
            int x = mineX(d);
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                for (int y = FLOOR; y <= top + 1; y++) {
                    boolean inside = d < 5 && y > FLOOR && y <= top && z >= z0 && z <= z1;
                    set(x, y, z, inside ? AIR : y == FLOOR ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                            : Math.floorMod(x * 7 + y * 13 + z * 5, 11) == 0 ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState() : deepslate);
                }
                for (int y = top + 2; y < SY && air(x, y, z); y++) {
                    set(x, y, z, deepslate);
                }
            }
        }
        // The draws the rest of the scene was laid out after: without them everything placed later would move.
        random.consumeCount(240);
        BlockState diamond = Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
        set(face, FLOOR + 1, mid, diamond);
        set(face, FLOOR + 2, mid, diamond);
        set(face, FLOOR + 2, mid + 1, diamond);
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int y = FLOOR + 1; y < top; y++) {
            set(frame, y, z0, post);
            set(frame, y, z1, post);
        }
        for (int z = z0; z <= z1; z++) {
            set(frame, top, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
        }
        set(near, FLOOR + 3, z0, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        set(far, FLOOR + 3, z1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
        set(near, FLOOR + 1, z1, Blocks.LANTERN.defaultBlockState());
        set(near, FLOOR + 1, z0, Blocks.BARREL.defaultBlockState());
        set(near, FLOOR + 1, z0 + 1, Blocks.BARREL.defaultBlockState());
        set(near, FLOOR + 2, z0, Blocks.LANTERN.defaultBlockState());
        for (int[] web : new int[][]{{far, z0}, {far, z1}, {mineX(3), z1}, {mineX(3), z0}}) {
            set(web[0], top, web[1], Blocks.COBWEB.defaultBlockState());
        }
        set(far, FLOOR + 1, z0, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(far, FLOOR + 1, z1, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(far, FLOOR + 2, z1, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(far, FLOOR + 1, z1 - 1, slab(Blocks.COBBLED_DEEPSLATE_SLAB, SlabType.BOTTOM));
        for (int d = -3; d <= 2; d++) {
            if (air(mineX(d), FLOOR + 1, mid)) {
                set(mineX(d), FLOOR + 1, mid, Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, RailShape.EAST_WEST));
            }
        }
        world.figures.add(new CaveWorld.Figure(MINE_X + 0.5F + MINE_DIR * 2.1F, FLOOR + 1, mid + 0.5F, MINE_DIR > 0 ? -90.0F : 90.0F,
                true, 0, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.TORCH), true));
        tunnelMouth(z0, z1, top);
    }

    /** The column {@code d} blocks into the mine cut from its mouth (negative: out in front of it). */
    private static int mineX(int d) {
        return MINE_X + MINE_DIR * d;
    }

    /**
     * A timber portal the whole width of the cut, braced at the top corners, a lamp post either side and a heap of
     * what's come out of it.
     */
    private void tunnelMouth(int z0, int z1, int top) {
        int x0 = MINE_X;
        BlockState post = log(Blocks.SPRUCE_LOG, Direction.Axis.Y);
        for (int y = FLOOR + 1; y <= top; y++) {
            set(x0, y, z0 - 1, post);
            set(x0, y, z1 + 1, post);
        }
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            set(x0, top + 1, z, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        }
        set(x0, top, z0, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, true));
        set(x0, top, z1, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, true));
        for (int z : new int[]{z0 - 1, z1 + 1}) {
            if (standable(mineX(-1), z)) {
                set(mineX(-1), FLOOR + 1, z, fence(false, false, false, false));
                set(mineX(-1), FLOOR + 2, z, fence(false, false, false, false));
                set(mineX(-1), FLOOR + 3, z, Blocks.LANTERN.defaultBlockState());
            }
        }
        BlockState[] spoil = {Blocks.GRAVEL.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
            Blocks.COAL_ORE.defaultBlockState(), Blocks.RAW_IRON_BLOCK.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()};
        int[][] heap = {{mineX(-2), z1 + 1}, {mineX(-3), z1 + 1}, {mineX(-2), z1 + 2}, {mineX(-4), z1 + 1}};
        for (int i = 0; i < heap.length; i++) {
            if (standable(heap[i][0], heap[i][1])) {
                set(heap[i][0], FLOOR + 1, heap[i][1], spoil[i % spoil.length]);
            }
        }
        if (get(mineX(-2), FLOOR + 1, z1 + 1).is(Blocks.GRAVEL)) {
            set(mineX(-2), FLOOR + 2, z1 + 1, slab(Blocks.COBBLED_DEEPSLATE_SLAB, SlabType.BOTTOM));
        }
    }

    /** Open ground at {@code (x, z)}: air on top of something solid and dry. */
    private boolean standable(int x, int z) {
        BlockState under = get(x, FLOOR, z);
        return air(x, FLOOR + 1, z) && air(x, FLOOR + 2, z) && !under.isAir() && under.getFluidState().isEmpty();
    }

    /**
     * A bay dug into the east wall off the south end of the boardwalk, domed and mossy: friends round a campfire on
     * stair benches, supplies behind.
     */
    private void camp() {
        int fx = 35;
        int fz = 52;
        for (int z = fz - 5; z <= fz + 5; z++) {
            for (int x = DOCK_X1 + 2; x <= fx + 6; x++) {
                float u = (x - fx - 0.5F) / 5.5F;
                float v = (z - fz) / 5.0F;
                float r = u * u * u * u + v * v * v * v;
                if (r >= 1.0F) {
                    continue;
                }
                for (int y = FLOOR + 1; y <= FLOOR + 6 + Math.round(2.0F * (1.0F - r)); y++) {
                    set(x, y, z, AIR);
                }
                if (rock(get(x, FLOOR, z))) {
                    set(x, FLOOR, z, Blocks.MOSS_BLOCK.defaultBlockState());
                }
            }
        }
        set(fx, FLOOR + 1, fz, Blocks.CAMPFIRE.defaultBlockState());
        set(fx, FLOOR, fz, Blocks.COBBLESTONE.defaultBlockState());
        set(fx - 2, FLOOR + 1, fz - 1, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        set(fx + 2, FLOOR + 1, fz + 2, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        set(fx + 3, FLOOR + 1, fz + 3, Blocks.BARREL.defaultBlockState());
        set(fx + 3, FLOOR + 1, fz + 4, Blocks.BARREL.defaultBlockState());
        set(fx + 3, FLOOR + 2, fz + 3, Blocks.LANTERN.defaultBlockState());
        float[][] friends = {{fx - 1.3F, fz}, {fx + 0.2F, fz + 2.4F}, {fx + 2.0F, fz - 0.8F}};
        ItemStack[] hands = {new ItemStack(Items.TORCH), new ItemStack(Items.BREAD), new ItemStack(Items.IRON_SHOVEL)};
        int[] skins = {2, 9, 14};
        for (int i = 0; i < friends.length; i++) {
            float yaw = (float) Math.toDegrees(Mth.atan2(-(fx + 0.5F - friends[i][0]), fz + 0.5F - friends[i][1]));
            world.figures.add(new CaveWorld.Figure(friends[i][0], FLOOR + 1, friends[i][1], yaw, false, skins[i], hands[i],
                    ItemStack.EMPTY, false));
        }
        for (int[] wood : new int[][]{{fx + 4, FLOOR + 1, fz}, {fx + 4, FLOOR + 1, fz + 1}, {fx + 4, FLOOR + 2, fz}}) {
            if (air(wood[0], wood[1], wood[2]) && !air(wood[0], wood[1] - 1, wood[2])) {
                set(wood[0], wood[1], wood[2], log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
            }
        }
    }

    /** A lantern hung from the underside of a blossom canopy, if there is one overhead. */
    private void canopyLantern(int x, int z) {
        for (int y = FLOOR + 4; y < SY - 1; y++) {
            if (get(x, y, z).is(Blocks.CHERRY_LEAVES)) {
                set(x, y - 1, z, hangingLantern());
                return;
            }
            if (!air(x, y, z)) {
                return;
            }
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
        workshopFittings(x0, x1, z0, z1);
    }

    /**
     * The workshop fitted out the way cave builders do it: the back wall timber-framed with a lamp set into the middle
     * post, beams across the roof braced off the wall, a shelf over the work blocks, pots and candles on the books,
     * and a rug with a lamp under it.
     */
    private void workshopFittings(int x0, int x1, int z0, int z1) {
        int mid = (z0 + z1) / 2;
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState post = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
        for (int z = z0; z <= z1; z++) {
            for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
                set(x1 + 1, y, z, z == z0 || z == mid || z == z1 ? post : planks);
            }
            set(x1 + 1, FLOOR + 5, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
            set(x0, FLOOR, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
        }
        set(x1 + 1, FLOOR + 3, mid, Blocks.SHROOMLIGHT.defaultBlockState());
        for (int z : new int[]{mid - 2, mid + 2}) {
            for (int x = x0 + 1; x <= x1; x++) {
                set(x, FLOOR + 5, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
            }
            set(x1, FLOOR + 4, z, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, true));
        }
        for (int z = z0 + 1; z <= mid; z++) {
            if (z != mid - 2) {
                set(x1, FLOOR + 3, z, slab(Blocks.SPRUCE_SLAB, SlabType.TOP));
            }
        }
        set(x1, FLOOR + 4, z0 + 1, Blocks.POTTED_FERN.defaultBlockState());
        set(x1, FLOOR + 4, mid - 1, candle(Blocks.WHITE_CANDLE, 3));
        set(x1, FLOOR + 3, mid + 1, Blocks.POTTED_AZALEA.defaultBlockState());
        set(x1, FLOOR + 3, z1 - 1, candle(Blocks.CANDLE, 2));
        for (int z = mid - 3; z <= mid + 1; z++) {
            for (int x = x0 + 1; x <= x0 + 3; x++) {
                boolean border = x != x0 + 2 || z == mid - 3 || z == mid + 1;
                if (air(x, FLOOR + 1, z)) {
                    set(x, FLOOR + 1, z, (border ? Blocks.RED_CARPET : Blocks.YELLOW_CARPET).defaultBlockState());
                }
            }
        }
        set(x0 + 2, FLOOR, mid - 1, Blocks.SHROOMLIGHT.defaultBlockState());
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
            set(x, FLOOR, z, Blocks.SHROOMLIGHT.defaultBlockState());
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
                if (nearFigure(x, z)) {
                    continue;
                }
                float r = random.nextFloat();
                Block plant = r < 0.03F ? Blocks.AZALEA : r < 0.045F ? Blocks.FLOWERING_AZALEA : r < 0.1F ? Blocks.FERN
                        : r < 0.19F ? Blocks.SHORT_GRASS : r < 0.196F ? Blocks.LANTERN : null;
                if (plant != null && !doorway(x, z)) {
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

    /** A candle on every few posts of the railings of the boardwalk and the bridge. */
    private void railCandles() {
        for (int z = DOCK_Z0; z < SZ; z++) {
            if (z % 4 == 1 && get(DOCK_X0, FLOOR + 1, z).is(Blocks.SPRUCE_FENCE) && air(DOCK_X0, FLOOR + 2, z)) {
                set(DOCK_X0, FLOOR + 2, z, railCandle());
            }
        }
        int x0 = Math.round(westShore(BRIDGE_Z)) - 2;
        for (int x = x0 + 2; x < DOCK_X0 - 1; x += 4) {
            for (int z : new int[]{BRIDGE_Z - 1, BRIDGE_Z + 1}) {
                if (get(x, FLOOR + 1, z).is(Blocks.SPRUCE_FENCE) && air(x, FLOOR + 2, z)) {
                    set(x, FLOOR + 2, z, railCandle());
                }
            }
        }
    }

    private BlockState railCandle() {
        return candle(random.nextFloat() < 0.8F ? Blocks.CANDLE : Blocks.PINK_CANDLE, 1);
    }

    /** Candles on the stones standing out of the pond. */
    private void stoneCandles() {
        for (int z = POND_Z0; z < SZ - 1; z++) {
            for (int x = 1; x < SX - 1; x++) {
                int wet = 0;
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    wet += get(x + side.getStepX(), WATER, z + side.getStepZ()).is(Blocks.WATER) ? 1 : 0;
                }
                if (wet < 3) {
                    continue;
                }
                for (int y = FLOOR + 2; y >= FLOOR; y--) {
                    BlockState state = get(x, y, z);
                    if (state.isAir()) {
                        continue;
                    }
                    if ((rock(state) || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.MOSS_BLOCK)) && random.nextFloat() < 0.6F) {
                        set(x, y + 1, z, candle(CANDLES[random.nextInt(CANDLES.length)], 1 + random.nextInt(3)));
                    }
                    break;
                }
            }
        }
    }

    /** Drifts of small pale flowers over the open moss, each mostly of one kind, kept clear of where people stand. */
    private void flowers(ImprovedNoise noise) {
        Block[] kinds = {Blocks.LILY_OF_THE_VALLEY, Blocks.AZURE_BLUET, Blocks.ALLIUM, Blocks.PINK_TULIP, Blocks.WHITE_TULIP,
            Blocks.OXEYE_DAISY, Blocks.CORNFLOWER};
        for (int z = 1; z < SZ - 1; z++) {
            for (int x = 1; x < SX - 1; x++) {
                BlockState above = get(x, FLOOR + 1, z);
                if (!get(x, FLOOR, z).is(Blocks.MOSS_BLOCK) || !(above.isAir() || above.is(Blocks.MOSS_CARPET) || above.is(Blocks.SHORT_GRASS))
                        || noise.noise(x * 0.21, 120.0, z * 0.21) < 0.2 || nearFigure(x, z) || random.nextFloat() > 0.5F) {
                    continue;
                }
                int drift = Math.floorMod((int) Math.floor(noise.noise(x * 0.08, 140.0, z * 0.08) * 10.0), kinds.length);
                Block kind = random.nextFloat() < 0.25F ? kinds[random.nextInt(kinds.length)] : kinds[drift];
                if (!doorway(x, z)) {
                    set(x, FLOOR + 1, z, kind.defaultBlockState());
                }
            }
        }
    }

    /** A few more spore blossoms in the roof over the water. */
    private void sporeBlossoms() {
        for (int z = POND_Z0; z < SZ; z++) {
            for (int x = 1; x < SX - 1; x++) {
                if (!get(x, WATER, z).is(Blocks.WATER) || random.nextFloat() > 0.03F) {
                    continue;
                }
                int top = ceiling(x, FLOOR + 1, z);
                BlockState roof = get(x, top + 1, z);
                if (top >= FLOOR + 7 && (rock(roof) || roof.is(Blocks.MOSS_BLOCK))) {
                    set(x, top, z, Blocks.SPORE_BLOSSOM.defaultBlockState());
                }
            }
        }
    }

    /**
     * An amethyst pocket the miner has broken into round the diamonds at the back of the cut, crystals growing off it
     * into the cut but not where the miner stands and swings.
     */
    private void geode() {
        int mid = (MINE_Z0 + MINE_Z1) / 2;
        for (int z = MINE_Z0 - 1; z <= MINE_Z1 + 1; z++) {
            for (int y = FLOOR + 1; y <= FLOOR + 6; y++) {
                for (int d = 2; d <= 5; d++) {
                    int x = mineX(d);
                    BlockState state = get(x, y, z);
                    if (!(state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.DEEPSLATE_IRON_ORE))
                            || random.nextFloat() > 0.5F) {
                        continue;
                    }
                    Direction open = null;
                    for (Direction side : Direction.values()) {
                        int ax = x + side.getStepX();
                        int ay = y + side.getStepY();
                        int az = z + side.getStepZ();
                        if (side != Direction.UP && (ax == mineX(3) || ax == mineX(4)) && air(ax, ay, az)
                                && (Math.abs(az - mid) >= 2 || ay >= FLOOR + 4)) {
                            open = side;
                        }
                    }
                    if (open == null) {
                        continue;
                    }
                    set(x, y, z, (random.nextFloat() < 0.25F ? Blocks.BUDDING_AMETHYST : Blocks.AMETHYST_BLOCK).defaultBlockState());
                    if (random.nextFloat() < 0.6F) {
                        Block crystal = random.nextFloat() < 0.5F ? Blocks.AMETHYST_CLUSTER
                                : random.nextFloat() < 0.5F ? Blocks.LARGE_AMETHYST_BUD : Blocks.MEDIUM_AMETHYST_BUD;
                        set(x + open.getStepX(), y + open.getStepY(), z + open.getStepZ(),
                                crystal.defaultBlockState().setValue(AmethystClusterBlock.FACING, open));
                    }
                }
            }
        }
    }
}
