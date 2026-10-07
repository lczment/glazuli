package com.lczme.liquidglass.cave;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** What structure processors see while a menu scene is built: the stand-in terrain plus everything placed so far. */
final class StructureReader implements LevelReader {
    private final RegistryAccess access;
    private final StructureBuilder builder;
    private final Holder<Biome> biome;
    private final BiomeManager biomes;
    private final WorldBorder border = new WorldBorder();

    StructureReader(RegistryAccess access, StructureBuilder builder) {
        this.access = access;
        this.builder = builder;
        this.biome = access.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        this.biomes = new BiomeManager(this, 0L);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return builder.get(pos);
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
    public int getHeight(Heightmap.Types type, int x, int z) {
        return builder.terrain.surface(x, z);
    }

    @Nullable
    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean require) {
        return null;
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public int getSkyDarken() {
        return 0;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return biomes;
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return biome;
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public DimensionType dimensionType() {
        return access.registryOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
    }

    @Override
    public RegistryAccess registryAccess() {
        return access;
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return FeatureFlags.DEFAULT_FLAGS;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return 1.0F;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return null;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return resolver.getColor(biome.value(), pos.getX(), pos.getZ());
    }

    @Override
    public WorldBorder getWorldBorder() {
        return border;
    }

    @Nullable
    @Override
    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB box) {
        return List.of();
    }

    @Override
    public int getHeight() {
        return 384;
    }

    @Override
    public int getMinBuildHeight() {
        return -64;
    }
}
