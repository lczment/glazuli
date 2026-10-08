package com.lczme.glazuli.cave;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * A small voxel world for the menu background, readable by vanilla's block and fluid renderers. Light is precomputed,
 * so no light engine is needed. Generated scenes are closed caves (stone all around); captured ones are open
 * ({@link #open}): their edge columns extend outwards, with sky above and stone below.
 */
public final class CaveWorld implements BlockAndTintGetter {
    /** Size of the generated scenes. */
    public static final int SIZE_X = 72;
    public static final int SIZE_Y = 40;
    public static final int SIZE_Z = 200;
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    final int sizeX;
    final int sizeY;
    final int sizeZ;
    final BlockState[] blocks;
    final byte[] light;
    @Nullable
    final byte[] sky;
    final boolean open;
    /** Built scenes aim their camera at the finished blocks, so they set it afterwards. */
    CameraRig rig;
    final SceneLook look;
    /** Block entity data by position (structure scenes); drawn only by the world renderer, see {@link #needsWorld()}. */
    final Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
    /** Open scenes: nothing past the edges (floating end islands) instead of the edge columns continuing. */
    boolean voidOutside;
    /** People in the scene; like block entities, only the world renderer draws them. */
    final List<Figure> figures = new ArrayList<>();

    /**
     * A person standing at {@code x, y, z} (feet) facing {@code yaw}: the player themself (their skin) or a miner with
     * one of the default skins picked by {@code skin}. {@code swings}: works with the main hand item, and turns to face
     * the camera when it comes close.
     */
    record Figure(float x, float y, float z, float yaw, boolean self, int skin, ItemStack mainHand, ItemStack offHand,
                  boolean swings) {
    }

    CaveWorld(int sizeX, int sizeY, int sizeZ, @Nullable byte[] sky, boolean open, CameraRig rig, SceneLook look) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = new BlockState[sizeX * sizeY * sizeZ];
        this.light = new byte[blocks.length];
        this.sky = sky;
        this.open = open;
        this.rig = rig;
        this.look = look;
    }

    boolean needsWorld() {
        return !blockEntities.isEmpty() || !figures.isEmpty();
    }

    CameraRig rig() {
        return rig;
    }

    SceneLook look() {
        return look;
    }

    boolean contains(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    int index(int x, int y, int z) {
        return (z * sizeY + y) * sizeX + x;
    }

    /** Full opaque cube: hides its neighbours' faces and blocks light. */
    static boolean isOpaqueCube(BlockState state) {
        return state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    BlockState get(int x, int y, int z) {
        if (contains(x, y, z)) {
            return blocks[index(x, y, z)];
        }
        if (voidOutside) {
            return AIR;
        }
        if (!open || y < 0) {
            return STONE;
        }
        return y >= sizeY ? AIR : blocks[index(Mth.clamp(x, 0, sizeX - 1), y, Mth.clamp(z, 0, sizeZ - 1))];
    }

    void set(int x, int y, int z, BlockState state) {
        if (contains(x, y, z)) {
            blocks[index(x, y, z)] = state;
        }
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return get(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        if (!shade) {
            return 1.0F;
        }
        return switch (direction) {
            case DOWN -> 0.5F;
            case UP -> 1.0F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
        };
    }

    /** Never used: brightness is overridden below. */
    @Nullable
    @Override
    public LevelLightEngine getLightEngine() {
        return null;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (layer == LightLayer.SKY) {
            if (sky == null || y < 0 && !voidOutside) {
                return 0;
            }
            if (voidOutside && !contains(x, y, z)) {
                return 15;
            }
            return y >= sizeY ? 15 : sky[index(Mth.clamp(x, 0, sizeX - 1), y, Mth.clamp(z, 0, sizeZ - 1))];
        }
        if (open && !voidOutside && y >= 0 && y < sizeY) {
            return light[index(Mth.clamp(x, 0, sizeX - 1), y, Mth.clamp(z, 0, sizeZ - 1))];
        }
        return contains(x, y, z) ? light[index(x, y, z)] : 0;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        return Math.max(getBrightness(LightLayer.BLOCK, pos), getBrightness(LightLayer.SKY, pos) - amount);
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return getBrightness(LightLayer.SKY, pos) >= 15;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        if (resolver == BiomeColors.WATER_COLOR_RESOLVER) {
            return look.water();
        }
        return resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER ? look.foliage() : look.grass();
    }

    @Override
    public int getHeight() {
        return sizeY;
    }

    @Override
    public int getMinBuildHeight() {
        return 0;
    }
}
