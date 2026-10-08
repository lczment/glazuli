package com.lczme.glazuli.cave;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.joml.Vector3f;

/**
 * A mine hall, like the cave a single chamber with a corner per menu: the rails crossing it (title), the player at the
 * diamond face (singleplayer), miners round a campfire (multiplayer), the workshop (options), an amethyst geode broken
 * open in the wall (mods) and a lava pool (everything else). The rails come in from the south and the camera dives
 * down the north tunnel.
 */
final class MineScene {
    private static final int SX = 64;
    private static final int SY = 32;
    private static final int SZ = 60;
    /** Top of the floor; the air starts one above. */
    private static final int FLOOR = 8;
    private static final float HALL_X = 32.0F;
    private static final float HALL_Z = 28.0F;
    private static final float HALL_RX = 23.0F;
    private static final float HALL_RZ = 21.0F;
    private static final float HALL_HEIGHT = 10.0F;
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
            {32.0F, 12.5F, 13.5F, 32.0F, 10.0F, 36.0F},
            {12.0F, 10.5F, 27.6F, 8.9F, 10.3F, 26.5F},
            {39.5F, 11.5F, 25.0F, 44.5F, 9.8F, 30.5F},
            {44.5F, 11.0F, 14.5F, 53.0F, 9.8F, 14.5F},
            {21.0F, 11.5F, 35.5F, 14.5F, 13.0F, 42.0F},
            {37.0F, 13.0F, 33.5F, 45.0F, 8.0F, 42.0F},
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

    private void air(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int z = z0; z <= z1; z++) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    set(x, y, z, AIR);
                }
            }
        }
    }

    private void blockEntity(int x, int y, int z, String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        world.blockEntities.put(new BlockPos(x, y, z), tag);
    }

    private void fill(long seed) {
        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed ^ 0x3A1EL));
        for (int z = 0; z < SZ; z++) {
            for (int y = 0; y < SY; y++) {
                for (int x = 0; x < SX; x++) {
                    double n = noise.noise(x * 0.11, y * 0.11, z * 0.11);
                    BlockState rock = y < 5 + n * 3.0 ? Blocks.DEEPSLATE.defaultBlockState()
                            : n > 0.42 ? Blocks.ANDESITE.defaultBlockState()
                            : n < -0.45 ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    world.set(x, y, z, rock);
                }
            }
        }
        ores();
        hall(noise);
        tunnels();
        geode();
        diamondFace();
        camp();
        workshop();
        lavaPool();
        lanterns();
    }

    private void ores() {
        Block[] kinds = {Blocks.COAL_ORE, Blocks.COAL_ORE, Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE,
            Blocks.GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE};
        for (int i = 0; i < 700; i++) {
            int cx = random.nextInt(SX);
            int cy = 4 + random.nextInt(SY - 8);
            int cz = random.nextInt(SZ);
            Block kind = kinds[random.nextInt(kinds.length)];
            int size = 2 + random.nextInt(5);
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

    /** The hall: a flat floor under a rough dome, scattered with gravel and cobble. */
    private void hall(ImprovedNoise noise) {
        BlockState[] floor = {Blocks.GRAVEL.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState()};
        for (int z = 0; z < SZ; z++) {
            for (int x = 0; x < SX; x++) {
                float u = (x + 0.5F - HALL_X) / HALL_RX;
                float v = (z + 0.5F - HALL_Z) / HALL_RZ;
                double r2 = u * u + v * v + noise.noise(x * 0.09, 5.0, z * 0.09) * 0.12;
                if (r2 >= 1.0) {
                    continue;
                }
                int ceiling = FLOOR + 1 + (int) (HALL_HEIGHT * Math.sqrt(1.0 - r2) + noise.noise(x * 0.2, 9.0, z * 0.2) * 2.0);
                for (int y = FLOOR + 1; y <= ceiling; y++) {
                    set(x, y, z, AIR);
                }
                if (random.nextFloat() < 0.18F) {
                    set(x, FLOOR, z, floor[random.nextInt(floor.length)]);
                }
            }
        }
    }

    private BlockState hangingLantern() {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    /** The rail tunnels in from the south and out to the north, timbered, with the rails running across the hall. */
    private void tunnels() {
        air(30, FLOOR + 1, 0, 34, FLOOR + 4, 10);
        air(30, FLOOR + 1, 46, 34, FLOOR + 4, SZ - 1);
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState beam = Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int z : new int[]{2, 6, 9, 47, 51, 55}) {
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                set(30, y, z, log);
                set(34, y, z, log);
            }
            for (int x = 30; x <= 34; x++) {
                set(x, FLOOR + 4, z, beam);
            }
            set(z < 30 ? 31 : 33, FLOOR + 3, z, hangingLantern());
        }
        for (int z = 0; z < SZ; z++) {
            if (world.get(32, FLOOR + 1, z).isAir() && random.nextFloat() < 0.96F) {
                set(32, FLOOR + 1, z, Blocks.RAIL.defaultBlockState());
            }
        }
        set(31, FLOOR + 1, 12, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
        blockEntity(31, FLOOR + 1, 12, "minecraft:chest");
        set(30, FLOOR + 1, 12, Blocks.BARREL.defaultBlockState());
        set(30, FLOOR + 2, 12, Blocks.BARREL.defaultBlockState());
    }

    /** The west wall: a vein of diamonds the player is digging out. */
    private void diamondFace() {
        air(8, FLOOR + 1, 24, 12, FLOOR + 4, 29);
        for (int z = 23; z <= 30; z++) {
            for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
                float r = random.nextFloat();
                BlockState face = Blocks.DEEPSLATE.defaultBlockState();
                if (z >= 25 && z <= 28 && y <= FLOOR + 3 && r < 0.7F) {
                    face = (r < 0.35F ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE).defaultBlockState();
                } else if (r < 0.15F) {
                    face = Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
                } else if (r < 0.25F) {
                    face = Blocks.DEEPSLATE_COAL_ORE.defaultBlockState();
                }
                set(7, y, z, face);
            }
        }
        set(10, FLOOR + 1, 24, Blocks.LANTERN.defaultBlockState());
        set(11, FLOOR + 1, 29, Blocks.COBBLESTONE.defaultBlockState());
        set(12, FLOOR + 1, 29, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(11, FLOOR + 2, 29, Blocks.COBBLESTONE.defaultBlockState());
        world.figures.add(new CaveWorld.Figure(8.9F, FLOOR + 1, 26.5F, 90.0F, true, 0,
                new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.TORCH), true));
    }

    /** East of the rails: miners round a campfire, a log to sit on. */
    private void camp() {
        set(44, FLOOR + 1, 30, Blocks.CAMPFIRE.defaultBlockState());
        BlockState bench = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        for (int z = 29; z <= 31; z++) {
            set(40, FLOOR + 1, z, bench);
        }
        set(48, FLOOR + 1, 34, Blocks.BARREL.defaultBlockState());
        set(49, FLOOR + 1, 34, Blocks.BARREL.defaultBlockState());
        set(48, FLOOR + 2, 34, Blocks.BARREL.defaultBlockState());
        float fx = 44.5F;
        float fz = 30.5F;
        float[][] miners = {{42.0F, 30.6F}, {44.6F, 33.1F}, {47.0F, 29.4F}};
        ItemStack[] hands = {new ItemStack(Items.TORCH), new ItemStack(Items.BREAD), new ItemStack(Items.IRON_SHOVEL)};
        int[] skins = {2, 9, 14};
        for (int i = 0; i < miners.length; i++) {
            float yaw = (float) Math.toDegrees(Mth.atan2(-(fx - miners[i][0]), fz - miners[i][1]));
            world.figures.add(new CaveWorld.Figure(miners[i][0], FLOOR + 1, miners[i][1], yaw, false, skins[i], hands[i],
                    ItemStack.EMPTY, false));
        }
    }

    /** A bay in the south-east wall with furnaces, an anvil and work tables. */
    private void workshop() {
        air(46, FLOOR + 1, 11, 53, FLOOR + 4, 18);
        BlockState west = Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST);
        set(53, FLOOR + 1, 12, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(53, FLOOR + 1, 13, west.setValue(AbstractFurnaceBlock.LIT, true));
        set(53, FLOOR + 1, 14, Blocks.BLAST_FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST)
                .setValue(AbstractFurnaceBlock.LIT, true));
        set(53, FLOOR + 1, 15, Blocks.SMOKER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        set(53, FLOOR + 1, 16, Blocks.SMITHING_TABLE.defaultBlockState());
        set(53, FLOOR + 2, 13, Blocks.LANTERN.defaultBlockState());
        set(51, FLOOR + 1, 17, Blocks.ANVIL.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        set(49, FLOOR + 1, 18, Blocks.GRINDSTONE.defaultBlockState());
        set(48, FLOOR + 1, 11, Blocks.BARREL.defaultBlockState());
        set(48, FLOOR + 2, 11, Blocks.BARREL.defaultBlockState());
        set(50, FLOOR + 1, 11, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        blockEntity(50, FLOOR + 1, 11, "minecraft:chest");
        set(50, FLOOR + 4, 14, hangingLantern());
    }

    /** An amethyst geode in the north-west wall, broken open where the hall cuts into it. */
    private void geode() {
        float cx = 15.0F;
        float cy = 13.0F;
        float cz = 41.0F;
        BlockState amethyst = Blocks.AMETHYST_BLOCK.defaultBlockState();
        BlockState budding = Blocks.BUDDING_AMETHYST.defaultBlockState();
        for (int z = 33; z <= 49; z++) {
            for (int y = 5; y <= 21; y++) {
                for (int x = 7; x <= 23; x++) {
                    float d = Mth.sqrt((x + 0.5F - cx) * (x + 0.5F - cx) + (y + 0.5F - cy) * (y + 0.5F - cy) + (z + 0.5F - cz) * (z + 0.5F - cz));
                    if (d < 4.2F) {
                        set(x, y, z, AIR);
                    } else if (d < 6.5F && !world.get(x, y, z).isAir()) {
                        set(x, y, z, d < 4.9F ? random.nextFloat() < 0.1F ? budding : amethyst
                                : d < 5.7F ? Blocks.CALCITE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState());
                    }
                }
            }
        }
        Block[] crystals = {Blocks.AMETHYST_CLUSTER, Blocks.LARGE_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.SMALL_AMETHYST_BUD};
        for (int z = 36; z <= 46; z++) {
            for (int y = 8; y <= 18; y++) {
                for (int x = 10; x <= 20; x++) {
                    if (!world.get(x, y, z).isAir() || random.nextFloat() > 0.3F) {
                        continue;
                    }
                    for (Direction side : Direction.values()) {
                        BlockState support = world.get(x - side.getStepX(), y - side.getStepY(), z - side.getStepZ());
                        if (support == amethyst || support == budding) {
                            set(x, y, z, crystals[random.nextInt(crystals.length)].defaultBlockState()
                                    .setValue(AmethystClusterBlock.FACING, side));
                            break;
                        }
                    }
                }
            }
        }
    }

    /** A pool of lava sunk into the floor in the north-east, rimmed with magma and basalt. */
    private void lavaPool() {
        for (int z = 36; z <= 47; z++) {
            for (int x = 39; x <= 51; x++) {
                float u = (x + 0.5F - 45.0F) / 6.0F;
                float v = (z + 0.5F - 41.5F) / 5.5F;
                float r2 = u * u + v * v;
                if (r2 < 1.0F && world.get(x, FLOOR + 1, z).isAir()) {
                    set(x, FLOOR, z, Blocks.LAVA.defaultBlockState());
                    set(x, FLOOR - 1, z, Blocks.LAVA.defaultBlockState());
                } else if (r2 < 1.35F && world.get(x, FLOOR + 1, z).isAir()) {
                    set(x, FLOOR, z, (random.nextBoolean() ? Blocks.MAGMA_BLOCK : Blocks.BASALT).defaultBlockState());
                }
            }
        }
    }

    /** Lanterns on chains from the dome, high enough that the camera passes below them. */
    private void lanterns() {
        int[][] spots = {{24, 20}, {32, 24}, {40, 20}, {26, 33}, {37, 37}, {32, 15}};
        for (int[] spot : spots) {
            int x = spot[0];
            int z = spot[1];
            int top = FLOOR + 1;
            while (top < SY - 1 && world.get(x, top + 1, z).isAir()) {
                top++;
            }
            int lantern = Math.max(FLOOR + 7, top - 3);
            if (lantern >= top) {
                continue;
            }
            for (int y = lantern + 1; y <= top; y++) {
                set(x, y, z, Blocks.CHAIN.defaultBlockState());
            }
            set(x, lantern, z, hangingLantern());
        }
    }
}
