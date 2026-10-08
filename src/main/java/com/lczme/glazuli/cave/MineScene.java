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
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.joml.Vector3f;

/**
 * An old mine, a place per menu: the timbered gallery (title), the player at the diamond face (singleplayer), miners
 * round a campfire (multiplayer), the workshop (options), an amethyst geode (mods) and the ravine the rails cross on a
 * trestle over lava (everything else). The side rooms are reached down the gallery, so the camera flies that way.
 */
final class MineScene {
    private static final int SX = 64;
    private static final int SY = 34;
    private static final int SZ = 76;
    /** Top of the gallery floor; the air starts one above. */
    private static final int FLOOR = 8;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

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
        world.rig = rig(world);
        return world;
    }

    private static CustomRig rig(CaveWorld world) {
        float[][] shots = {
            {31.8F, 11.4F, 10.0F, 31.0F, 10.6F, 34.0F},
            {14.3F, 10.5F, 26.6F, 11.5F, 10.3F, 25.5F},
            {42.3F, 11.0F, 33.8F, 45.5F, 9.9F, 37.5F},
            {35.5F, 11.0F, 15.0F, 41.0F, 9.8F, 15.0F},
            {18.6F, 12.2F, 47.5F, 12.5F, 14.5F, 46.0F},
            {25.0F, 14.0F, 64.5F, 35.0F, 5.0F, 66.0F},
        };
        Vector3f[] eyes = new Vector3f[shots.length];
        Vector3f[] looks = new Vector3f[shots.length];
        for (int i = 0; i < shots.length; i++) {
            eyes[i] = new Vector3f(shots[i][0], shots[i][1], shots[i][2]);
            looks[i] = new Vector3f(shots[i][3], shots[i][4], shots[i][5]);
        }
        CustomRig rig = new CustomRig(eyes, looks);
        rig.route(CaveCamera.Spot.WORLDS, new Vector3f(31.0F, 11.0F, 25.0F), new Vector3f(24.0F, 10.6F, 25.0F));
        rig.route(CaveCamera.Spot.SERVERS, new Vector3f(31.0F, 11.0F, 36.0F), new Vector3f(37.5F, 10.6F, 36.0F));
        rig.route(CaveCamera.Spot.OPTIONS, new Vector3f(31.0F, 11.0F, 15.0F), new Vector3f(33.5F, 11.0F, 15.0F));
        rig.route(CaveCamera.Spot.MODS, new Vector3f(31.0F, 11.0F, 47.0F), new Vector3f(24.0F, 10.6F, 47.0F));
        rig.route(CaveCamera.Spot.OTHER, new Vector3f(31.0F, 11.0F, 57.0F), new Vector3f(31.0F, 12.5F, 62.0F));
        rig.bind(world);
        return rig;
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

    /** Knocks out some rock along the walls and ceiling of {@code x0..z1}, so rooms aren't boxes. */
    private void roughen(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            for (int y = y0; y <= y1 + 1; y++) {
                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    boolean edge = x < x0 || x > x1 || z < z0 || z > z1 || y > y1;
                    if (edge && world.contains(x, y, z) && !world.get(x, y, z).isAir() && random.nextFloat() < 0.35F) {
                        set(x, y, z, AIR);
                    }
                }
            }
        }
    }

    private void fill(long seed) {
        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed ^ 0x3A1EL));
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        for (int z = 0; z < SZ; z++) {
            for (int y = 0; y < SY; y++) {
                for (int x = 0; x < SX; x++) {
                    double n = noise.noise(x * 0.11, y * 0.11, z * 0.11);
                    BlockState rock = y < 5 + n * 3.0 ? deepslate
                            : n > 0.42 ? Blocks.ANDESITE.defaultBlockState() : n < -0.45 ? Blocks.TUFF.defaultBlockState() : STONE;
                    world.set(x, y, z, rock);
                }
            }
        }
        ores();
        gallery();
        diamondFace();
        camp();
        workshop();
        geode();
        ravine(noise);
    }

    private void ores() {
        Block[] kinds = {Blocks.COAL_ORE, Blocks.COAL_ORE, Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE,
            Blocks.GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE};
        for (int i = 0; i < 900; i++) {
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
        if (ore == Blocks.COAL_ORE) {
            return Blocks.DEEPSLATE_COAL_ORE.defaultBlockState();
        }
        if (ore == Blocks.IRON_ORE) {
            return Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
        }
        if (ore == Blocks.COPPER_ORE) {
            return Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState();
        }
        if (ore == Blocks.GOLD_ORE) {
            return Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
        }
        if (ore == Blocks.REDSTONE_ORE) {
            return Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
        }
        if (ore == Blocks.LAPIS_ORE) {
            return Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState();
        }
        return ore.defaultBlockState();
    }

    private BlockState hangingLantern() {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    /** The main gallery along z: timber frames, lanterns, rails down the middle, a little clutter. */
    private void gallery() {
        air(28, FLOOR + 1, 3, 34, FLOOR + 5, 60);
        BlockState[] floor = {Blocks.GRAVEL.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState()};
        for (int z = 3; z <= 60; z++) {
            for (int x = 28; x <= 34; x++) {
                if (random.nextFloat() < 0.18F) {
                    set(x, FLOOR, z, floor[random.nextInt(floor.length)]);
                }
            }
            if (random.nextFloat() < 0.95F) {
                set(31, FLOOR + 1, z, Blocks.RAIL.defaultBlockState());
            }
        }
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState beam = Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        int frame = 0;
        for (int z = 6; z <= 54; z += 6, frame++) {
            for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
                set(28, y, z, log);
                set(34, y, z, log);
            }
            for (int x = 28; x <= 34; x++) {
                set(x, FLOOR + 5, z, beam);
            }
            set(29, FLOOR + 4, z, hangingLantern());
            set(33, FLOOR + 4, z, hangingLantern());
            set(frame % 2 == 0 ? 28 : 34, FLOOR + 1, z + 3, Blocks.LANTERN.defaultBlockState());
        }
        for (int z = 4; z <= 59; z++) {
            for (int x : new int[]{28, 34}) {
                if (z % 6 != 0 && random.nextFloat() < 0.12F) {
                    set(x, FLOOR + 5, z, Blocks.COBWEB.defaultBlockState());
                }
            }
        }
        set(29, FLOOR + 1, 21, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
        blockEntity(29, FLOOR + 1, 21, "minecraft:chest");
        set(29, FLOOR + 1, 22, Blocks.BARREL.defaultBlockState());
        set(33, FLOOR + 1, 40, Blocks.BARREL.defaultBlockState());
        set(33, FLOOR + 2, 40, Blocks.BARREL.defaultBlockState());
        set(33, FLOOR + 1, 41, Blocks.COBBLESTONE.defaultBlockState());
    }

    private void blockEntity(int x, int y, int z, String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        world.blockEntities.put(new BlockPos(x, y, z), tag);
    }

    /** West of the gallery: the room where the player is digging out a diamond vein. */
    private void diamondFace() {
        air(20, FLOOR + 1, 24, 27, FLOOR + 4, 26);
        air(10, FLOOR + 1, 20, 20, FLOOR + 6, 31);
        roughen(11, FLOOR + 1, 21, 20, FLOOR + 6, 31);
        for (int z = 21; z <= 30; z++) {
            for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
                float r = random.nextFloat();
                BlockState face = Blocks.DEEPSLATE.defaultBlockState();
                if (z >= 24 && z <= 27 && y <= FLOOR + 3 && r < 0.7F) {
                    face = (r < 0.35F ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE).defaultBlockState();
                } else if (r < 0.15F) {
                    face = Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
                } else if (r < 0.25F) {
                    face = Blocks.DEEPSLATE_COAL_ORE.defaultBlockState();
                }
                set(9, y, z, face);
            }
        }
        set(12, FLOOR + 1, 22, Blocks.LANTERN.defaultBlockState());
        set(18, FLOOR + 1, 29, Blocks.LANTERN.defaultBlockState());
        set(13, FLOOR + 1, 28, Blocks.COBBLESTONE.defaultBlockState());
        set(13, FLOOR + 1, 29, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        set(14, FLOOR + 1, 29, Blocks.COBBLESTONE.defaultBlockState());
        set(19, FLOOR + 1, 21, Blocks.CRAFTING_TABLE.defaultBlockState());
        world.figures.add(new CaveWorld.Figure(11.5F, FLOOR + 1, 25.5F, 90.0F, true, 0,
                new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.TORCH), true));
    }

    /** East of the gallery: miners round a campfire. */
    private void camp() {
        air(35, FLOOR + 1, 35, 38, FLOOR + 4, 37);
        air(39, FLOOR + 1, 29, 52, FLOOR + 6, 44);
        roughen(39, FLOOR + 1, 29, 52, FLOOR + 6, 44);
        set(45, FLOOR + 1, 37, Blocks.CAMPFIRE.defaultBlockState());
        BlockState bench = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        for (int z = 36; z <= 38; z++) {
            set(41, FLOOR + 1, z, bench);
        }
        set(50, FLOOR + 1, 42, Blocks.LANTERN.defaultBlockState());
        set(41, FLOOR + 1, 31, Blocks.LANTERN.defaultBlockState());
        set(51, FLOOR + 1, 31, Blocks.BARREL.defaultBlockState());
        set(51, FLOOR + 1, 32, Blocks.BARREL.defaultBlockState());
        set(51, FLOOR + 2, 31, Blocks.BARREL.defaultBlockState());
        float fx = 45.5F;
        float fz = 37.5F;
        float[][] miners = {{43.0F, 37.5F}, {45.6F, 40.1F}, {48.0F, 36.4F}};
        ItemStack[] hands = {new ItemStack(Items.TORCH), new ItemStack(Items.BREAD), new ItemStack(Items.IRON_SHOVEL)};
        int[] skins = {2, 9, 14};
        for (int i = 0; i < miners.length; i++) {
            float yaw = (float) Math.toDegrees(Mth.atan2(-(fx - miners[i][0]), fz - miners[i][1]));
            world.figures.add(new CaveWorld.Figure(miners[i][0], FLOOR + 1, miners[i][1], yaw, false, skins[i], hands[i],
                    ItemStack.EMPTY, false));
        }
    }

    /** A bay off the gallery with furnaces, an anvil and work tables. */
    private void workshop() {
        air(35, FLOOR + 1, 12, 41, FLOOR + 4, 18);
        BlockState west = Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST);
        set(41, FLOOR + 1, 13, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(41, FLOOR + 1, 14, west.setValue(AbstractFurnaceBlock.LIT, true));
        set(41, FLOOR + 1, 15, Blocks.BLAST_FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST)
                .setValue(AbstractFurnaceBlock.LIT, true));
        set(41, FLOOR + 1, 16, Blocks.SMOKER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        set(41, FLOOR + 1, 17, Blocks.SMITHING_TABLE.defaultBlockState());
        set(41, FLOOR + 2, 14, Blocks.LANTERN.defaultBlockState());
        set(39, FLOOR + 1, 17, Blocks.ANVIL.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        set(37, FLOOR + 1, 18, Blocks.GRINDSTONE.defaultBlockState());
        set(36, FLOOR + 1, 12, Blocks.BARREL.defaultBlockState());
        set(36, FLOOR + 2, 12, Blocks.BARREL.defaultBlockState());
        set(38, FLOOR + 1, 12, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        blockEntity(38, FLOOR + 1, 12, "minecraft:chest");
        set(39, FLOOR + 4, 14, hangingLantern());
        set(36, FLOOR + 1, 17, Blocks.LANTERN.defaultBlockState());
    }

    /** West of the far gallery: an amethyst geode the miners broke into. */
    private void geode() {
        float cx = 16.0F;
        float cy = 14.0F;
        float cz = 47.0F;
        BlockState basalt = Blocks.SMOOTH_BASALT.defaultBlockState();
        BlockState calcite = Blocks.CALCITE.defaultBlockState();
        BlockState amethyst = Blocks.AMETHYST_BLOCK.defaultBlockState();
        BlockState budding = Blocks.BUDDING_AMETHYST.defaultBlockState();
        for (int z = 39; z <= 55; z++) {
            for (int y = 6; y <= 22; y++) {
                for (int x = 8; x <= 24; x++) {
                    float d = Mth.sqrt((x + 0.5F - cx) * (x + 0.5F - cx) + (y + 0.5F - cy) * (y + 0.5F - cy) + (z + 0.5F - cz) * (z + 0.5F - cz));
                    if (d < 4.2F) {
                        set(x, y, z, AIR);
                    } else if (d < 4.9F) {
                        set(x, y, z, random.nextFloat() < 0.1F ? budding : amethyst);
                    } else if (d < 5.7F) {
                        set(x, y, z, calcite);
                    } else if (d < 6.5F) {
                        set(x, y, z, basalt);
                    }
                }
            }
        }
        air(20, FLOOR + 1, 46, 27, FLOOR + 4, 48);
        Block[] crystals = {Blocks.AMETHYST_CLUSTER, Blocks.LARGE_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.SMALL_AMETHYST_BUD};
        for (int z = 42; z <= 52; z++) {
            for (int y = 9; y <= 19; y++) {
                for (int x = 11; x <= 21; x++) {
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
        set(16, 13, 47, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 9));
    }

    /** The gallery ends at a ravine with lava far below; the rails go on over a timber trestle. */
    private void ravine(ImprovedNoise noise) {
        for (int x = 12; x <= 50; x++) {
            int top = 26 - Math.abs(x - 31) / 3;
            for (int z = 60; z <= 70; z++) {
                double n = noise.noise(x * 0.15, 3.0, z * 0.15);
                int half = 4 + (int) Math.round(n * 1.5) - Math.max(0, Math.abs(x - 31) - 14) / 2;
                if (Math.abs(z - 65) > half) {
                    continue;
                }
                for (int y = 1; y <= top; y++) {
                    set(x, y, z, y <= 3 ? Blocks.LAVA.defaultBlockState() : AIR);
                }
            }
        }
        air(29, FLOOR + 1, 70, 33, FLOOR + 4, 74);
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        for (int z = 59; z <= 71; z++) {
            for (int x = 30; x <= 32; x++) {
                if (world.get(x, FLOOR, z).isAir() || world.get(x, FLOOR, z).is(Blocks.LAVA)) {
                    set(x, FLOOR, z, planks);
                }
            }
            set(31, FLOOR + 1, z, Blocks.RAIL.defaultBlockState());
            if (z % 2 == 0 && z >= 61 && z <= 69) {
                set(30, FLOOR + 1, z, fence);
                set(32, FLOOR + 1, z, fence);
            }
        }
        for (int z : new int[]{62, 66, 69}) {
            for (int y = 1; y < FLOOR; y++) {
                set(30, y, z, log);
                set(32, y, z, log);
            }
        }
        for (int z = 71; z <= 74; z++) {
            set(31, FLOOR + 1, z, Blocks.RAIL.defaultBlockState());
        }
    }
}
