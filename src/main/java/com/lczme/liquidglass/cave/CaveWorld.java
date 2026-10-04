package com.lczme.liquidglass.cave;

import javax.annotation.Nullable;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * A small fixed voxel world for the menu background, readable by vanilla's block and fluid renderers. Light is
 * precomputed (block light only, there is no sky), so no light engine is needed.
 */
public final class CaveWorld implements BlockAndTintGetter {
    public static final int SIZE_X = 72;
    public static final int SIZE_Y = 40;
    public static final int SIZE_Z = 200;
    private static final BlockState OUTSIDE = Blocks.STONE.defaultBlockState();

    final BlockState[] blocks = new BlockState[SIZE_X * SIZE_Y * SIZE_Z];
    final byte[] light = new byte[SIZE_X * SIZE_Y * SIZE_Z];
    final CaveLayout layout;

    CaveWorld(CaveLayout layout) {
        this.layout = layout;
    }

    public CaveLayout layout() {
        return layout;
    }

    static boolean inBounds(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < SIZE_X && y < SIZE_Y && z < SIZE_Z;
    }

    static int index(int x, int y, int z) {
        return (z * SIZE_Y + y) * SIZE_X + x;
    }

    BlockState get(int x, int y, int z) {
        return inBounds(x, y, z) ? blocks[index(x, y, z)] : OUTSIDE;
    }

    void set(int x, int y, int z, BlockState state) {
        if (inBounds(x, y, z)) {
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
        if (layer == LightLayer.SKY || !inBounds(pos.getX(), pos.getY(), pos.getZ())) {
            return 0;
        }
        return light[index(pos.getX(), pos.getY(), pos.getZ())];
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        return getBrightness(LightLayer.BLOCK, pos);
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return false;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        if (resolver == BiomeColors.WATER_COLOR_RESOLVER) {
            return 0x3F76E4;
        }
        return resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER ? 0x59AE30 : 0x79C05A;
    }

    @Override
    public int getHeight() {
        return SIZE_Y;
    }

    @Override
    public int getMinBuildHeight() {
        return 0;
    }
}
