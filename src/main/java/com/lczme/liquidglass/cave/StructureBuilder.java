package com.lczme.liquidglass.cave;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.mixin.SinglePoolElementAccessor;
import com.lczme.liquidglass.mixin.StructureTemplateAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Lays out vanilla structures with vanilla's own generators (jigsaw assembly for villages, ancient cities, trial
 * chambers and bastions; the end city's tower recipe) over a flat stand-in terrain, then places their templates the way
 * {@link StructureTemplate#placeInWorld} does: same palettes, processors (street paths, cracked bricks, oxidised
 * copper), rotations and block entity data, written into a block map instead of a server level.
 */
final class StructureBuilder {
    /** The terrain the structures are fitted to, in world coordinates; also what processors see before placement. */
    interface Terrain {
        BlockState block(int x, int y, int z);

        /** First free y of a column (vanilla's WORLD_SURFACE heightmap value). */
        int surface(int x, int z);
    }

    /** A scene's ground, in scene coordinates. */
    interface Fill {
        BlockState block(int x, int y, int z);
    }

    private final RegistryAccess access;
    private final StructureTemplateManager templates;
    final Terrain terrain;
    final Map<BlockPos, BlockState> blocks = new HashMap<>();
    final Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
    final List<StructurePiece> pieces = new ArrayList<>();
    private final StructureReader reader;

    StructureBuilder(RegistryAccess access, StructureTemplateManager templates, Terrain terrain) {
        this.access = access;
        this.templates = templates;
        this.terrain = terrain;
        this.reader = new StructureReader(access, this);
    }

    static StructureBuilder create(Terrain terrain) throws java.io.IOException {
        return new StructureBuilder(VanillaData.registries().join(), VanillaData.templates(), terrain);
    }

    StructureReader reader() {
        return reader;
    }

    BlockState get(BlockPos pos) {
        BlockState placed = blocks.get(pos);
        return placed != null ? placed : terrain.block(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Assembles a jigsaw structure from {@code pool} at {@code start} (y is the ground surface for {@code project}ed
     * starts, absolute otherwise), {@code depth} connections deep, and places it.
     */
    StructureBuilder jigsaw(String pool, @Nullable String startJigsaw, int depth, BlockPos start, boolean project,
                            boolean expansionHack, int maxDistance, long seed) {
        Holder<StructureTemplatePool> startPool = access.registryOrThrow(Registries.TEMPLATE_POOL)
                .getHolderOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.withDefaultNamespace(pool)));
        int ground = terrain.surface(start.getX(), start.getZ()) - 1;
        LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
        Holder<Biome> plains = access.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        // Only the ground height matters to the generator: projected starts and terrain-matching pieces sit on it.
        FlatLevelGeneratorSettings flat = new FlatLevelGeneratorSettings(Optional.empty(), plains, List.of())
                .withBiomeAndLayers(List.of(new FlatLayerInfo(ground + 64, Blocks.STONE), new FlatLayerInfo(1, Blocks.GRASS_BLOCK)),
                        Optional.empty(), plains);
        FlatLevelSource generator = new FlatLevelSource(flat);
        RandomState random = RandomState.create(access.asGetterLookup(), NoiseGeneratorSettings.OVERWORLD, seed);
        Structure.GenerationContext context = new Structure.GenerationContext(access, generator, generator.getBiomeSource(),
                random, templates, seed, new ChunkPos(start), height, biome -> true);
        BlockPos origin = project ? new BlockPos(start.getX(), 0, start.getZ()) : start;
        Optional<Structure.GenerationStub> stub = JigsawPlacement.addPieces(context, startPool,
                Optional.ofNullable(startJigsaw).map(ResourceLocation::parse), depth, origin, expansionHack,
                project ? Optional.of(Heightmap.Types.WORLD_SURFACE_WG) : Optional.empty(), maxDistance,
                PoolAliasLookup.EMPTY, JigsawStructure.DEFAULT_DIMENSION_PADDING, JigsawStructure.DEFAULT_LIQUID_SETTINGS);
        if (stub.isEmpty()) {
            throw new IllegalStateException("No " + pool + " generated");
        }
        List<StructurePiece> generated = stub.get().getPiecesBuilder().build().pieces();
        place(generated);
        return this;
    }

    /** An end city tower grown from {@code start} (its base floor's corner), and places it. */
    StructureBuilder endCity(BlockPos start, Rotation rotation, long seed) {
        List<StructurePiece> generated = new ArrayList<>();
        EndCityPieces.startHouseTower(templates, start, rotation, generated, RandomSource.create(seed));
        place(generated);
        return this;
    }

    private void place(List<StructurePiece> generated) {
        pieces.addAll(generated);
        for (StructurePiece piece : generated) {
            if (piece instanceof PoolElementStructurePiece pool) {
                placeElement(pool.getElement(), pool.getPosition(), pool.getRotation(), pool.getBoundingBox());
            } else if (piece instanceof TemplateStructurePiece template) {
                placeTemplate(template.template(), template.placeSettings(), template.templatePosition(), template.templatePosition());
            }
        }
    }

    private void placeElement(StructurePoolElement element, BlockPos position, Rotation rotation, BoundingBox box) {
        // Feature elements (hay piles, trees) and empty ones place nothing here.
        if (element instanceof SinglePoolElement single) {
            SinglePoolElementAccessor accessor = (SinglePoolElementAccessor) single;
            StructureTemplate template = accessor.liquidglass$template().map(templates::getOrCreate, t -> t);
            StructurePlaceSettings settings = accessor.liquidglass$settings(rotation, box, JigsawStructure.DEFAULT_LIQUID_SETTINGS, false);
            placeTemplate(template, settings, position, position);
        }
    }

    private void placeTemplate(StructureTemplate template, StructurePlaceSettings settings, BlockPos offset, BlockPos pivot) {
        List<StructureTemplate.Palette> palettes = ((StructureTemplateAccessor) template).liquidglass$palettes();
        if (palettes.isEmpty()) {
            return;
        }
        BoundingBox box = settings.getBoundingBox();
        for (StructureTemplate.StructureBlockInfo raw : settings.getRandomPalette(palettes, offset).blocks()) {
            BlockPos pos = StructureTemplate.calculateRelativePosition(settings, raw.pos()).offset(offset);
            StructureTemplate.StructureBlockInfo info = new StructureTemplate.StructureBlockInfo(pos, raw.state(),
                    raw.nbt() != null ? raw.nbt().copy() : null);
            for (StructureProcessor processor : settings.getProcessors()) {
                try {
                    info = processor.process(reader, offset, pivot, raw, info, settings, template);
                } catch (RuntimeException e) {
                    // Processors written for a server level (loot, entities) may not cope; keep the block as is.
                    LiquidGlass.LOGGER.debug("Structure processor {} failed in a menu scene", processor, e);
                }
                if (info == null) {
                    break;
                }
            }
            if (info == null || box != null && !box.isInside(info.pos())) {
                continue;
            }
            BlockState state = info.state().mirror(settings.getMirror()).rotate(settings.getRotation());
            if (state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            if (state.hasProperty(BlockStateProperties.WATERLOGGED) && get(info.pos()).getFluidState().isSource()) {
                state = state.setValue(BlockStateProperties.WATERLOGGED, true);
            }
            set(info.pos(), state, info.nbt());
        }
    }

    void set(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntity) {
        BlockPos key = pos.immutable();
        blocks.put(key, state);
        if (blockEntity != null && state.hasBlockEntity()) {
            blockEntities.put(key, blockEntity);
        } else {
            blockEntities.remove(key);
        }
    }

    void set(BlockPos pos, BlockState state) {
        set(pos, state, null);
    }

    void set(int x, int y, int z, Block block) {
        set(new BlockPos(x, y, z), block.defaultBlockState(), null);
    }

    /** All placed blocks' bounds, or {@code null} if nothing was placed. */
    @Nullable
    BoundingBox bounds() {
        return BoundingBox.encapsulatingPositions(blocks.keySet()).orElse(null);
    }

    /** The bounding box of the first (start) piece. */
    BoundingBox startBox() {
        return pieces.get(0).getBoundingBox();
    }

    /**
     * Writes {@code ground} (scene coordinates) and the structure into a new scene covering {@code area} (world
     * coordinates), shifted so the area's minimum corner is the scene origin. The camera is set afterwards.
     */
    CaveWorld toWorld(BoundingBox area, boolean open, SceneLook look, Fill ground) {
        int sx = area.getXSpan();
        int sy = area.getYSpan();
        int sz = area.getZSpan();
        CaveWorld world = new CaveWorld(sx, sy, sz, open ? new byte[sx * sy * sz] : null, open, null, look);
        for (int z = 0; z < sz; z++) {
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    world.blocks[world.index(x, y, z)] = ground.block(x, y, z);
                }
            }
        }
        BlockPos min = new BlockPos(area.minX(), area.minY(), area.minZ());
        blocks.forEach((pos, state) -> {
            if (area.isInside(pos)) {
                world.set(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ(), state);
            }
        });
        blockEntities.forEach((pos, tag) -> {
            if (area.isInside(pos)) {
                world.blockEntities.put(pos.subtract(min), tag);
            }
        });
        return world;
    }
}
