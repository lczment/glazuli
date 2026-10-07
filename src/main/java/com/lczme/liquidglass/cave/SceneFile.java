package com.lczme.liquidglass.cave;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Captured menu scenes: the blocks and light around the player plus their view, as compressed NBT in
 * {@code config/liquidglass/scenes}. Block entities (chests, signs, ...) aren't kept; the scene has no world behind it.
 */
public final class SceneFile {
    private static final int VERSION = 1;
    private static final int RADIUS = 56;
    private static final int BELOW = 20;
    private static final int ABOVE = 32;
    /** Eye sky light below this counts as underground: dark fog instead of the sky colour. */
    private static final int OUTDOOR_SKY = 8;
    private static final int CAVE_FOG = 0x050608;

    private SceneFile() {
    }

    /** Snapshots the area around the player; on the render thread (reads the client level). */
    public static CompoundTag capture(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        Vec3 eye = player.getEyePosition();
        int ox = Mth.floor(eye.x) - RADIUS;
        int oz = Mth.floor(eye.z) - RADIUS;
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(eye.y) - BELOW);
        int maxY = Math.min(level.getMaxBuildHeight(), Mth.floor(eye.y) + ABOVE);
        int sx = 2 * RADIUS;
        int sy = maxY - minY;
        int sz = 2 * RADIUS;

        Object2IntOpenHashMap<BlockState> ids = new Object2IntOpenHashMap<>();
        ListTag palette = new ListTag();
        int[] blocks = new int[sx * sy * sz];
        byte[] blockLight = new byte[blocks.length];
        byte[] skyLight = new byte[blocks.length];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int z = 0; z < sz; z++) {
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++, i++) {
                    pos.set(ox + x, minY + y, oz + z);
                    BlockState state = level.getBlockState(pos);
                    blocks[i] = ids.computeIfAbsent(state, s -> {
                        palette.add(NbtUtils.writeBlockState((BlockState) s));
                        return palette.size() - 1;
                    });
                    blockLight[i] = (byte) level.getBrightness(LightLayer.BLOCK, pos);
                    skyLight[i] = (byte) level.getBrightness(LightLayer.SKY, pos);
                }
            }
        }

        BlockPos eyePos = BlockPos.containing(eye);
        boolean outdoor = level.getBrightness(LightLayer.SKY, eyePos) >= OUTDOOR_SKY;
        Vec3 sky = level.getSkyColor(eye, 1.0F);
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.putInt("SizeX", sx);
        tag.putInt("SizeY", sy);
        tag.putInt("SizeZ", sz);
        tag.put("Palette", palette);
        tag.putIntArray("Blocks", blocks);
        tag.putByteArray("BlockLight", blockLight);
        tag.putByteArray("SkyLight", skyLight);
        tag.putFloat("EyeX", (float) (eye.x - ox));
        tag.putFloat("EyeY", (float) (eye.y - minY));
        tag.putFloat("EyeZ", (float) (eye.z - oz));
        tag.putFloat("Yaw", player.getYRot());
        tag.putFloat("Pitch", player.getXRot());
        tag.putLong("DayTime", level.getDayTime() % 24000L);
        tag.putFloat("SkyBrightness", level.getSkyDarken(1.0F));
        tag.putInt("Fog", outdoor ? rgb(sky) : CAVE_FOG);
        tag.putBoolean("Outdoor", outdoor);
        tag.putInt("Water", BiomeColors.getAverageWaterColor(level, eyePos));
        tag.putInt("Grass", BiomeColors.getAverageGrassColor(level, eyePos));
        tag.putInt("Foliage", BiomeColors.getAverageFoliageColor(level, eyePos));
        return tag;
    }

    private static int rgb(Vec3 color) {
        return (Mth.clamp((int) (color.x * 255.0), 0, 255) << 16) | (Mth.clamp((int) (color.y * 255.0), 0, 255) << 8)
                | Mth.clamp((int) (color.z * 255.0), 0, 255);
    }

    public static void write(CompoundTag tag, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        NbtIo.writeCompressed(tag, temp);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    static CaveWorld read(Path file) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        int sx = tag.getInt("SizeX");
        int sy = tag.getInt("SizeY");
        int sz = tag.getInt("SizeZ");
        int[] ids = tag.getIntArray("Blocks");
        byte[] blockLight = tag.getByteArray("BlockLight");
        byte[] skyLight = tag.getByteArray("SkyLight");
        int volume = sx * sy * sz;
        if (sx <= 0 || sy <= 0 || sz <= 0 || ids.length != volume || blockLight.length != volume || skyLight.length != volume) {
            throw new IOException("Malformed scene " + file);
        }
        ListTag paletteTag = tag.getList("Palette", Tag.TAG_COMPOUND);
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i));
        }

        boolean outdoor = tag.getBoolean("Outdoor");
        float skyBrightness = Mth.clamp(tag.getFloat("SkyBrightness"), 0.0F, 1.0F);
        SceneLook look = new SceneLook(tag.getInt("Fog"), outdoor ? 20.0F : 12.0F, outdoor ? RADIUS + 8.0F : CaveCamera.FOG_END,
                1.00F, 0.82F, 0.60F, 0.10F, 0.11F, 0.13F, skyBrightness,
                tag.getInt("Water"), tag.getInt("Grass"), tag.getInt("Foliage"), tag.getLong("DayTime"), SceneLook.Dimension.OVERWORLD);
        CustomRig rig = new CustomRig(new Vector3f(tag.getFloat("EyeX"), tag.getFloat("EyeY"), tag.getFloat("EyeZ")),
                tag.getFloat("Yaw"), tag.getFloat("Pitch"));
        CaveWorld world = new CaveWorld(sx, sy, sz, skyLight, true, rig, look);
        rig.bind(world);
        for (int i = 0; i < volume; i++) {
            int id = ids[i];
            world.blocks[i] = id >= 0 && id < palette.length ? palette[id] : Blocks.AIR.defaultBlockState();
        }
        System.arraycopy(blockLight, 0, world.light, 0, volume);
        return world;
    }
}
