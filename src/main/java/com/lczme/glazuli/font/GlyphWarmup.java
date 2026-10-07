package com.lczme.glazuli.font;

import com.lczme.glazuli.intro.IntroState;
import com.lczme.glazuli.mixin.FontManagerAccessor;
import com.lczme.glazuli.mixin.MinecraftAccessor;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Bakes the glyphs of the current language ahead of use, most frequent first, a little per frame on the title screens and the pause menu.
 * A glyph is rasterised and uploaded the first time it is drawn, about 0.1 ms each for CJK at UI scale, so a screen
 * full of new text would otherwise stall its first frame (and its opening animation) by ten milliseconds or more.
 */
public final class GlyphWarmup {
    private static final long BUDGET_NANOS = 750_000L;
    private static final int MAX_GLYPHS = 6000;

    @Nullable
    private static Language language;
    @Nullable
    private static CompletableFuture<int[]> ranking;
    @Nullable
    private static int[] codepoints;
    @Nullable
    private static FontSet fontSet;
    @Nullable
    private static BakedGlyph probe;
    private static int next;

    private GlyphWarmup() {
    }

    public static void onRenderFramePost(RenderFrameEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !(mc.screen instanceof PauseScreen) || mc.getOverlay() != null || !IntroState.isDone()) {
            return;
        }
        Language current = Language.getInstance();
        if (current != language) {
            language = current;
            codepoints = null;
            Map<String, String> data = current instanceof ClientLanguage client ? client.getLanguageData() : Map.of();
            ranking = CompletableFuture.supplyAsync(() -> rank(data), Util.backgroundExecutor());
        }
        if (codepoints == null) {
            if (ranking == null || !ranking.isDone()) {
                return;
            }
            codepoints = ranking.getNow(new int[0]);
            next = 0;
        }
        FontSet set = ((FontManagerAccessor) ((MinecraftAccessor) mc).glazuli$getFontManager()).glazuli$getFontSet(Minecraft.DEFAULT_FONT);
        // A font reload empties the glyph cache in place; the first warmed glyph then comes back as a new object.
        if (set != fontSet || probe != null && codepoints.length > 0 && set.getGlyph(codepoints[0]) != probe) {
            fontSet = set;
            probe = null;
            next = 0;
        }
        if (next >= codepoints.length) {
            return;
        }
        long deadline = System.nanoTime() + BUDGET_NANOS;
        do {
            // Metrics are cached apart from the baked glyph, and text layout asks for them first.
            set.getGlyphInfo(codepoints[next], false);
            BakedGlyph glyph = set.getGlyph(codepoints[next]);
            if (next == 0) {
                probe = glyph;
            }
            next++;
        } while (next < codepoints.length && System.nanoTime() < deadline);    }

    private static int[] rank(Map<String, String> data) {
        Int2IntOpenHashMap counts = new Int2IntOpenHashMap();
        for (String value : data.values()) {
            value.codePoints().forEach(cp -> {
                if (cp > ' ') {
                    counts.addTo(cp, 1);
                }
            });
        }
        return counts.int2IntEntrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()))
                .limit(MAX_GLYPHS)
                .mapToInt(Int2IntMap.Entry::getIntKey)
                .toArray();
    }
}
