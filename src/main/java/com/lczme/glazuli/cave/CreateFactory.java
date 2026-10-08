package com.lczme.glazuli.cave;

import com.lczme.glazuli.Glazuli;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.joml.Vector3f;

/**
 * A riverside Create workshop: two water wheels, one geared up through a large and a small cogwheel to an overhead line
 * shaft driving presses, a drill and a mixer, the other turning the belt under the presses. Everything is looked up by
 * name, so the class only runs with Create installed; the parts turn because each block entity carries its speed.
 */
final class CreateFactory {
    private static final int GROUND = 3;
    private static final float WHEEL_RPM = 8.0F;
    private static final int OX = 28;
    private static final int OZ = 26;

    private final CaveWorld world;
    private final Map<Block, String> blockEntityIds = new HashMap<>();

    private CreateFactory(CaveWorld world) {
        this.world = world;
    }

    private static Block create(String name) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", name));
        if (block == Blocks.AIR) {
            throw new IllegalStateException("Create block " + name + " missing");
        }
        return block;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, String name, String value) {
        @SuppressWarnings("unchecked")
        Property<T> property = (Property<T>) state.getBlock().getStateDefinition().getProperty(name);
        Optional<T> parsed = property != null ? property.getValue(value) : Optional.empty();
        if (parsed.isEmpty()) {
            Glazuli.LOGGER.warn("Create factory scene: {} has no {}={}", state.getBlock(), name, value);
            return state;
        }
        return state.setValue(property, parsed.get());
    }

    private static BlockState state(String name, String... properties) {
        BlockState state = create(name).defaultBlockState();
        for (int i = 0; i + 1 < properties.length; i += 2) {
            state = with(state, properties[i], properties[i + 1]);
        }
        return state;
    }

    @Nullable
    private String blockEntityId(BlockState state) {
        return blockEntityIds.computeIfAbsent(state.getBlock(), block -> {
            for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
                if (type.isValid(state)) {
                    ResourceLocation key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
                    return key != null ? key.toString() : null;
                }
            }
            return null;
        });
    }

    /** Places a Create block whose block entity turns at {@code rpm}; returns its data for extra fields. */
    /** Places a Create block whose block entity turns at {@code rpm} (workshop coordinates); returns its data. */
    private CompoundTag kinetic(int x, int y, int z, BlockState state, float rpm) {
        put(x, y, z, state);
        CompoundTag tag = new CompoundTag();
        String id = blockEntityId(state);
        if (id != null) {
            tag.putString("id", id);
            tag.putFloat("Speed", rpm);
            world.blockEntities.put(new BlockPos(x + OX, y, z + OZ), tag);
        }
        return tag;
    }

    private void block(int x, int y, int z, BlockState state) {
        if (state.hasBlockEntity()) {
            kinetic(x, y, z, state, 0.0F);
        } else {
            put(x, y, z, state);
        }
    }

    /** Workshop coordinates: the workshop's corner sits at ({@link #OX}, {@link #OZ}) in the scene. */
    private void put(int x, int y, int z, BlockState state) {
        world.set(x + OX, y, z + OZ, state);
    }

    static CaveWorld build(long seed) {
        int sx = 96;
        int sy = 26;
        int sz = 84;
        SceneLook look = SceneLook.outdoor(0xBFD8F0, 160.0F, 1.0F, 0x91BD59, 0x77AB2F, 2500L);
        CaveWorld world = new CaveWorld(sx, sy, sz, new byte[sx * sy * sz], true, null, look);
        java.util.Arrays.fill(world.blocks, Blocks.AIR.defaultBlockState());
        new CreateFactory(world).fill(seed);
        CaveGenerator.skyLight(world);
        CaveGenerator.light(world);
        // Per menu, in Spot order: the whole workshop, the water wheels from across the river, the presses over the
        // belt, high over the east end, the mixer, and the drill from the west.
        float[][] shots = {
            {21.5F, 7.5F, 22.0F, 17.5F, 0.5F, 6.0F},
            {17.0F, 4.5F, -4.0F, 17.0F, 1.5F, 6.0F},
            {10.5F, 5.5F, 21.5F, 10.5F, 2.0F, 16.0F},
            {40.0F, 12.0F, 26.0F, 18.0F, 1.0F, 12.0F},
            {35.5F, 5.0F, 20.5F, 32.0F, 4.0F, 16.0F},
            {-1.0F, 3.5F, 19.0F, 8.0F, 3.0F, 15.0F},
        };
        Vector3f[] eyes = new Vector3f[shots.length];
        Vector3f[] looks = new Vector3f[shots.length];
        for (int i = 0; i < shots.length; i++) {
            float[] s = shots[i];
            eyes[i] = new Vector3f(OX + s[0], GROUND + s[1], OZ + s[2]);
            looks[i] = new Vector3f(OX + s[3], GROUND + s[4], OZ + s[5]);
        }
        CustomRig rig = new CustomRig(eyes, looks);
        rig.bind(world);
        world.rig = rig;
        return world;
    }

    private void fill(long seed) {
        RandomSource random = RandomSource.create(seed);
        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed ^ 0xFAC7L));
        int g = GROUND;
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        int[] height = new int[world.sizeX * world.sizeZ];
        for (int z = 0; z < world.sizeZ; z++) {
            for (int x = 0; x < world.sizeX; x++) {
                int lz = z - OZ;
                boolean river = lz >= 2 && lz <= 6;
                // Hills rise away from the workshop; the river cuts its valley through them.
                int ddx = Math.max(Math.max(OX - 6 - x, x - (OX + 44)), 0);
                int ddz = Math.max(Math.max(OZ - 4 - z, z - (OZ + 30)), 0);
                double rise = Math.min(Math.max(0.0, Math.sqrt(ddx * ddx + ddz * ddz) - 4.0) * 0.4, 12.0)
                        * (0.6 + 0.5 * noise.noise(x * 0.04, 0.0, z * 0.04));
                int h = river || lz == 1 || lz == 7 ? g : g + (int) Math.max(0, Math.round(rise));
                height[z * world.sizeX + x] = h;
                for (int y = 0; y <= h; y++) {
                    BlockState state;
                    if (river) {
                        state = y == 0 ? Blocks.GRAVEL.defaultBlockState() : water;
                    } else {
                        state = y == h ? grass : y < h - 3 || y == 0 ? Blocks.STONE.defaultBlockState() : dirt;
                    }
                    world.set(x, y, z, state);
                }
                if (lz == 1 || lz == 7) {
                    world.set(x, g, z, Blocks.MUD_BRICKS.defaultBlockState());
                }
            }
        }
        // Workshop floor and frame.
        BlockState floor = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int z = 12; z <= 21; z++) {
            for (int x = 2; x <= 37; x++) {
                put(x, g, z, (x + z) % 7 == 0 ? Blocks.ANDESITE.defaultBlockState() : floor);
            }
        }
        int roof = g + 8;
        // Only the back of the frame, so the camera sees in from the open front.
        for (int x = 2; x <= 37; x += 7) {
            for (int y = g + 1; y < roof; y++) {
                put(x, y, 12, log);
            }
        }
        BlockState beamX = log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState beamZ = log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        for (int x = 2; x <= 37; x++) {
            put(x, roof, 12, beamX);
        }
        for (int x = 2; x <= 37; x += 7) {
            put(x, roof, 13, beamZ);
        }
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        for (int x = 5; x <= 34; x += 7) {
            put(x, roof - 1, 13, Blocks.CHAIN.defaultBlockState());
            put(x, roof - 2, 13, lantern);
        }

        String east = "east";
        // Water wheel 1 (geared up for the line shaft) and water wheel 2 (the belt), both turning on the z axis.
        // Large wheels without their structure blocks: those only collide, they draw nothing.
        BlockState wheel = state("large_water_wheel", "axis", "z", "extension", "false");
        CompoundTag material = NbtUtils.writeBlockState(Blocks.SPRUCE_PLANKS.defaultBlockState());
        kinetic(14, g + 1, 4, wheel, WHEEL_RPM).put("Material", material.copy());
        kinetic(20, g + 1, 4, wheel, WHEEL_RPM).put("Material", material.copy());
        BlockState shaftZ = state("shaft", "axis", "z");
        BlockState shaftX = state("shaft", "axis", "x");
        BlockState shaftY = state("shaft", "axis", "y");
        for (int z = 5; z <= 10; z++) {
            kinetic(14, g + 1, z, shaftZ, WHEEL_RPM);
        }
        for (int z = 5; z <= 15; z++) {
            kinetic(20, g + 1, z, shaftZ, WHEEL_RPM);
        }
        // Large to small cogwheel: twice the speed, the other way round.
        float fast = -2.0F * WHEEL_RPM;
        for (int x = 13; x <= 15; x++) {
            put(x, g, 11, Blocks.AIR.defaultBlockState());
            put(x, g - 1, 11, Blocks.COBBLESTONE.defaultBlockState());
        }
        kinetic(14, g + 1, 11, state("large_cogwheel", "axis", "z"), WHEEL_RPM);
        kinetic(15, g + 2, 11, state("cogwheel", "axis", "z"), fast);
        for (int z = 12; z <= 15; z++) {
            kinetic(15, g + 2, z, shaftZ, fast);
        }
        // Up a level through two gearboxes onto the line shaft running along x.
        int line = g + 3;
        kinetic(15, g + 2, 16, state("gearbox", "axis", "x"), fast);
        kinetic(15, line, 16, state("gearbox", "axis", "z"), fast);
        for (int x = 5; x <= 30; x++) {
            if (x != 15 && x != 9 && x != 12) {
                kinetic(x, line, 16, shaftX, fast);
            }
        }
        for (int x : new int[]{9, 12}) {
            kinetic(x, line, 16, state("mechanical_press", "facing", east), fast);
        }
        kinetic(4, line, 16, state("mechanical_drill", "facing", "west"), fast);
        put(3, line, 16, Blocks.COBBLESTONE.defaultBlockState());
        put(3, line - 1, 16, Blocks.COBBLESTONE.defaultBlockState());
        put(3, line - 2, 16, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        // The east end turns up into a cogwheel meshing with the mixer over its basin.
        kinetic(31, line, 16, state("gearbox", "axis", "z"), fast);
        kinetic(31, line + 1, 16, shaftY, fast);
        kinetic(31, line + 2, 16, state("cogwheel", "axis", "y"), fast);
        kinetic(32, line + 2, 16, state("mechanical_mixer"), -fast);
        block(32, line, 16, state("basin"));
        BlockState casing = state("andesite_casing");
        put(32, g + 1, 16, casing);
        put(32, g + 2, 16, casing);
        // The belt under the presses, driven by water wheel 2's shaft at its east pulley.
        int beltStart = 7;
        int beltEnd = 20;
        int length = beltEnd - beltStart + 1;
        for (int x = beltStart; x <= beltEnd; x++) {
            String part = x == beltStart ? "start" : x == beltEnd ? "end" : "middle";
            CompoundTag belt = kinetic(x, g + 1, 16, state("belt", "facing", east, "slope", "horizontal", "part", part), WHEEL_RPM);
            belt.put("Controller", NbtUtils.writeBlockPos(new BlockPos(beltStart + OX, g + 1, 16 + OZ)));
            belt.putBoolean("IsController", x == beltStart);
            belt.putInt("Length", length);
            belt.putInt("Index", x - beltStart);
            belt.putString("Casing", "NONE");
            belt.putBoolean("Covered", false);
        }
        // Supports under the line shaft and some clutter.
        for (int x : new int[]{6, 18, 24, 29}) {
            for (int y = g + 1; y < line; y++) {
                put(x, y, 17, casing);
            }
            put(x, line, 17, casing);
        }
        put(23, g + 1, 19, Blocks.BARREL.defaultBlockState());
        put(24, g + 1, 19, Blocks.BARREL.defaultBlockState());
        put(24, g + 2, 19, Blocks.BARREL.defaultBlockState());
        put(26, g + 1, 19, Blocks.HAY_BLOCK.defaultBlockState());
        put(27, g + 1, 13, Blocks.CRAFTING_TABLE.defaultBlockState());
        put(28, g + 1, 13, Blocks.SMITHING_TABLE.defaultBlockState());
        // Trees and grass all around, clear of the workshop and the camera's view of it.
        for (int z = 2; z < world.sizeZ - 2; z++) {
            for (int x = 2; x < world.sizeX - 2; x++) {
                int h = height[z * world.sizeX + x];
                if (world.get(x, h, z) != grass || !world.get(x, h + 1, z).isAir()) {
                    continue;
                }
                boolean workshop = x >= OX - 4 && x <= OX + 42 && z >= OZ - 10 && z <= OZ + 30;
                float roll = random.nextFloat();
                if (!workshop && roll < 0.025F) {
                    boolean birch = random.nextInt(4) == 0;
                    tree(random, x, h + 1, z, birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG, birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES);
                } else if (roll < 0.25F) {
                    world.set(x, h + 1, z, random.nextInt(12) == 0 ? Blocks.DANDELION.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }

    private void tree(RandomSource random, int x, int y, int z, Block log, Block leaves) {
        BlockState leaf = leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int height = 4 + random.nextInt(2);
        for (int dy = height - 2; dy <= height + 1; dy++) {
            int r = dy >= height ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r && Math.abs(dz) == r && (dy >= height || random.nextBoolean())) {
                        continue;
                    }
                    if (world.contains(x + dx, y + dy, z + dz) && world.get(x + dx, y + dy, z + dz).isAir()) {
                        world.set(x + dx, y + dy, z + dz, leaf);
                    }
                }
            }
        }
        for (int dy = 0; dy < height; dy++) {
            world.set(x, y + dy, z, log.defaultBlockState());
        }
    }
}
