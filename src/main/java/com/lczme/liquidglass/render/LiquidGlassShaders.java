package com.lczme.liquidglass.render;

import com.lczme.liquidglass.LiquidGlass;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * Core shaders used by the glass renderer. Handles are replaced on every resource reload and are
 * {@code null} while unavailable (e.g. a compile error), in which case callers must fall back.
 */
public final class LiquidGlassShaders {
    @Nullable
    private static ShaderInstance kawaseDown;
    @Nullable
    private static ShaderInstance kawaseUp;
    @Nullable
    private static ShaderInstance glassPanel;
    @Nullable
    private static ShaderInstance screenFade;

    private LiquidGlassShaders() {
    }

    public static void onRegisterShaders(RegisterShadersEvent event) {
        // The previous instances are closed by vanilla during the reload.
        kawaseDown = null;
        kawaseUp = null;
        glassPanel = null;
        register(event, "kawase_down", DefaultVertexFormat.BLIT_SCREEN, s -> kawaseDown = s);
        register(event, "kawase_up", DefaultVertexFormat.BLIT_SCREEN, s -> kawaseUp = s);
        register(event, "glass_panel", DefaultVertexFormat.POSITION_TEX, s -> glassPanel = s);
        screenFade = null;
        register(event, "screen_fade", DefaultVertexFormat.BLIT_SCREEN, s -> screenFade = s);
    }

    @Nullable
    public static ShaderInstance screenFade() {
        return screenFade;
    }

    private static void register(RegisterShadersEvent event, String name, VertexFormat format, Consumer<ShaderInstance> onLoaded) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), LiquidGlass.id(name), format), onLoaded);
        } catch (IOException | RuntimeException e) {
            // Swallowed so a broken shader degrades to the flat fallback instead of failing the whole reload.
            LiquidGlass.LOGGER.error("Failed to load liquid glass shader '{}'", name, e);
        }
    }

    public static boolean isReady() {
        return kawaseDown != null && kawaseUp != null && glassPanel != null;
    }

    @Nullable
    public static ShaderInstance kawaseDown() {
        return kawaseDown;
    }

    @Nullable
    public static ShaderInstance kawaseUp() {
        return kawaseUp;
    }

    @Nullable
    public static ShaderInstance glassPanel() {
        return glassPanel;
    }
}
