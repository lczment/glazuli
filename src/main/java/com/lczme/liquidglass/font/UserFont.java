package com.lczme.liquidglass.font;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.config.LiquidGlassConfig.FontWeight;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.neoforged.fml.loading.FMLPaths;

/**
 * A font that takes precedence over the bundled HarmonyOS Sans: any font file the player drops into
 * {@code config/liquidglass/fonts}, otherwise PingFang if the system has it (PingFang cannot be redistributed).
 * Collections ({@code .ttc}) are reduced to a single face, since Minecraft always loads face 0.
 */
record UserFont(Path path, int face) {
    @Nullable
    private static volatile UserFont lastLogged;

    static Path dropInDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(LiquidGlass.MODID).resolve("fonts");
    }

    @Nullable
    static UserFont find(FontWeight weight) {
        try {
            Files.createDirectories(dropInDirectory());
        } catch (IOException ignored) {
        }
        Path file = firstFont(dropInDirectory(), null);
        if (file == null) {
            for (Path dir : systemFontDirectories()) {
                file = firstFont(dir, weight);
                if (file != null) {
                    break;
                }
            }
        }
        if (file == null) {
            return null;
        }
        try {
            UserFont font = new UserFont(file, chooseFace(file, weight));
            if (!font.equals(lastLogged)) {
                lastLogged = font;
                LiquidGlass.LOGGER.info("Using font {} (face {}) ahead of HarmonyOS Sans", file, font.face());
            }
            return font;
        } catch (IOException | RuntimeException e) {
            LiquidGlass.LOGGER.warn("Ignoring unreadable font {}", file, e);
            return null;
        }
    }

    InputStream open() throws IOException {
        byte[] data = Files.readAllBytes(this.path);
        return new ByteArrayInputStream(isCollection(data) ? extractFace(data, this.face) : data);
    }

    private static List<Path> systemFontDirectories() {
        List<Path> dirs = new ArrayList<>();
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            dirs.add(Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts"));
            String local = System.getenv("LOCALAPPDATA");
            if (local != null) {
                dirs.add(Path.of(local, "Microsoft", "Windows", "Fonts"));
            }
        } else if (os.contains("mac")) {
            dirs.add(Path.of("/System/Library/Fonts"));
            dirs.add(Path.of("/Library/Fonts"));
            dirs.add(Path.of(home, "Library", "Fonts"));
            // Newer macOS versions download PingFang as a mobile asset.
            dirs.add(Path.of("/System/Library/AssetsV2/com_apple_MobileAsset_Font7"));
        } else {
            dirs.add(Path.of(home, ".local", "share", "fonts"));
            dirs.add(Path.of(home, ".fonts"));
            dirs.add(Path.of("/usr/share/fonts"));
        }
        return dirs;
    }

    /**
     * Any font file when {@code pingFangWeight} is null (the drop-in folder); otherwise only PingFang, preferring the
     * Simplified Chinese file of that weight among PingFang's per-weight files.
     */
    @Nullable
    private static Path firstFont(Path dir, @Nullable FontWeight pingFangWeight) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        boolean pingFangOnly = pingFangWeight != null;
        try (Stream<Path> files = Files.find(dir, pingFangOnly ? 4 : 1, (path, attrs) -> attrs.isRegularFile() && isFontFile(path, pingFangOnly))) {
            Comparator<Path> order = pingFangOnly ? Comparator.comparingInt(path -> pingFangRank(path, pingFangWeight)) : (a, b) -> 0;
            return files.min(order.thenComparing(Path::toString)).orElse(null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isFontFile(Path path, boolean pingFangOnly) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean font = name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc");
        return font && (!pingFangOnly || name.contains("pingfang") || name.contains("苹方"));
    }

    private static int pingFangRank(Path path, FontWeight weight) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int rank = 0;
        if (!name.endsWith(".ttc") && Arrays.stream(weight.fileWords()).noneMatch(name::contains)) {
            rank += 2;
        }
        if (!name.contains("sc")) {
            rank += 1;
        }
        return rank;
    }

    private static boolean isCollection(byte[] data) {
        return data.length >= 12 && data[0] == 't' && data[1] == 't' && data[2] == 'c' && data[3] == 'f';
    }

    private static int chooseFace(Path file, FontWeight weight) throws IOException {
        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ttc")) {
            return 0;
        }
        ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(file));
        int count = buf.getInt(8);
        int fallback = 0;
        for (int i = 0; i < count; i++) {
            String name = postScriptName(buf, buf.getInt(12 + 4 * i));
            if (weight.pingFangFace().equals(name)) {
                return i;
            }
            if (fallback == 0 && name != null && name.endsWith("-Regular")) {
                fallback = i;
            }
        }
        return fallback;
    }

    @Nullable
    private static String postScriptName(ByteBuffer buf, int fontOffset) {
        int numTables = Short.toUnsignedInt(buf.getShort(fontOffset + 4));
        for (int t = 0; t < numTables; t++) {
            int record = fontOffset + 12 + 16 * t;
            if (buf.getInt(record) != 0x6E616D65) { // 'name'
                continue;
            }
            int table = buf.getInt(record + 8);
            int count = Short.toUnsignedInt(buf.getShort(table + 2));
            int strings = table + Short.toUnsignedInt(buf.getShort(table + 4));
            for (int n = 0; n < count; n++) {
                int entry = table + 6 + 12 * n;
                int platform = Short.toUnsignedInt(buf.getShort(entry));
                int nameId = Short.toUnsignedInt(buf.getShort(entry + 6));
                if (nameId != 6) {
                    continue;
                }
                byte[] raw = new byte[Short.toUnsignedInt(buf.getShort(entry + 8))];
                buf.get(strings + Short.toUnsignedInt(buf.getShort(entry + 10)), raw);
                return new String(raw, platform == 1 ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_16BE);
            }
        }
        return null;
    }

    /** Rebuilds face {@code index} of a collection as a standalone sfnt by copying its tables. */
    private static byte[] extractFace(byte[] data, int index) {
        ByteBuffer in = ByteBuffer.wrap(data);
        int fontOffset = in.getInt(12 + 4 * index);
        int numTables = Short.toUnsignedInt(in.getShort(fontOffset + 4));
        int headerSize = 12 + 16 * numTables;
        int total = headerSize;
        for (int t = 0; t < numTables; t++) {
            total += (in.getInt(fontOffset + 12 + 16 * t + 12) + 3) & ~3;
        }
        ByteBuffer out = ByteBuffer.allocate(total);
        out.put(data, fontOffset, 12);
        int dataOffset = headerSize;
        for (int t = 0; t < numTables; t++) {
            int record = fontOffset + 12 + 16 * t;
            int offset = in.getInt(record + 8);
            int length = in.getInt(record + 12);
            out.putInt(12 + 16 * t, in.getInt(record));
            out.putInt(12 + 16 * t + 4, in.getInt(record + 4));
            out.putInt(12 + 16 * t + 8, dataOffset);
            out.putInt(12 + 16 * t + 12, length);
            out.put(dataOffset, data, offset, length);
            dataOffset += (length + 3) & ~3;
        }
        return out.array();
    }
}
