package com.lczme.liquidglass.cave;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Static GPU mesh of a {@link CaveWorld}, one vertex buffer per chunk render layer. */
public final class CaveMesh implements AutoCloseable {
    static final List<RenderType> LAYERS = List.of(RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout(), RenderType.translucent());

    private final Map<RenderType, VertexBuffer> buffers;

    private CaveMesh(Map<RenderType, VertexBuffer> buffers) {
        this.buffers = buffers;
    }

    @Nullable
    VertexBuffer buffer(RenderType layer) {
        return buffers.get(layer);
    }

    @Override
    public void close() {
        buffers.values().forEach(VertexBuffer::close);
        buffers.clear();
    }

    /** CPU-side vertex data, built off-thread and uploaded with {@link #upload()} on the render thread. */
    public static final class Built implements AutoCloseable {
        private final Map<RenderType, ByteBufferBuilder> memory = new LinkedHashMap<>();
        private final Map<RenderType, MeshData> meshes = new LinkedHashMap<>();

        public CaveMesh upload() {
            Map<RenderType, VertexBuffer> buffers = new LinkedHashMap<>();
            meshes.forEach((layer, mesh) -> {
                VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                buffer.upload(mesh);
                buffers.put(layer, buffer);
            });
            VertexBuffer.unbind();
            meshes.clear();
            close();
            return new CaveMesh(buffers);
        }

        @Override
        public void close() {
            meshes.values().forEach(MeshData::close);
            meshes.clear();
            memory.values().forEach(ByteBufferBuilder::close);
            memory.clear();
        }
    }

    public static Built build(CaveWorld world) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        Built built = new Built();
        for (RenderType layer : LAYERS) {
            ByteBufferBuilder memory = new ByteBufferBuilder(1 << 20);
            built.memory.put(layer, memory);
            builders.put(layer, new BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK));
        }
        PoseStack pose = new PoseStack();
        RandomSource random = RandomSource.create();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < world.sizeZ; z++) {
            for (int y = 0; y < world.sizeY; y++) {
                for (int x = 0; x < world.sizeX; x++) {
                    BlockState state = world.get(x, y, z);
                    if (state.isAir() || isBuried(world, x, y, z, state)) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (!state.getFluidState().isEmpty()) {
                        RenderType layer = ItemBlockRenderTypes.getRenderLayer(state.getFluidState());
                        BufferBuilder builder = builders.get(layer);
                        if (builder != null) {
                            // The liquid renderer emits section-relative positions (pos & 15).
                            dispatcher.renderLiquid(pos, world, new Offset(builder, x & ~15, y & ~15, z & ~15), state, state.getFluidState());
                        }
                    }
                    if (state.getRenderShape() != RenderShape.MODEL) {
                        continue;
                    }
                    BakedModel model = dispatcher.getBlockModel(state);
                    random.setSeed(state.getSeed(pos));
                    for (RenderType layer : model.getRenderTypes(state, random, ModelData.EMPTY)) {
                        BufferBuilder builder = builders.get(layer);
                        if (builder == null) {
                            continue;
                        }
                        pose.pushPose();
                        pose.translate(x, y, z);
                        dispatcher.renderBatched(state, pos, world, pose, builder, true, random, ModelData.EMPTY, layer);
                        pose.popPose();
                    }
                }
            }
        }
        builders.forEach((layer, builder) -> {
            MeshData mesh = builder.build();
            if (mesh != null) {                built.meshes.put(layer, mesh);
            }
        });
        return built;
    }

    /**
     * Opaque cube fully enclosed by opaque cubes: no face can be visible, skip the (costly) tessellation. Full cubes
     * only: {@code canOcclude} is also true for carpets, and a block next to one would lose its visible face.
     */
    private static boolean isBuried(CaveWorld world, int x, int y, int z, BlockState state) {
        if (!CaveWorld.isOpaqueCube(state)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (!CaveWorld.isOpaqueCube(world.get(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ()))) {
                return false;
            }
        }
        return true;
    }

    private record Offset(VertexConsumer delegate, float dx, float dy, float dz) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x + dx, y + dy, z + dz);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
