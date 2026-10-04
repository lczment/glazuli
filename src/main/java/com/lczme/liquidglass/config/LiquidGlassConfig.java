package com.lczme.liquidglass.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class LiquidGlassConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_GLASS;
    public static final ModConfigSpec.EnumValue<BlurQuality> BLUR_QUALITY;
    public static final ModConfigSpec.BooleanValue ENABLE_REFRACTION;
    public static final ModConfigSpec.EnumValue<IntroMode> INTRO_MODE;
    public static final ModConfigSpec.BooleanValue STYLE_RELOAD_OVERLAY;
    public static final ModConfigSpec.BooleanValue DARK_EARLY_WINDOW;

    public static final ModConfigSpec.BooleanValue CUSTOM_TITLE_SCREEN;
    public static final ModConfigSpec.BooleanValue GLASS_BUTTONS;
    public static final ModConfigSpec.BooleanValue SCREEN_FADE;
    public static final ModConfigSpec.BooleanValue CAVE_BACKGROUND;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("glass");
        ENABLE_GLASS = builder
                .comment("Render UI panels with the liquid glass effect. When disabled, a flat translucent fallback is used.")
                .define("enableGlass", true);
        BLUR_QUALITY = builder
                .comment("Backdrop blur quality. Higher values cost more GPU time.")
                .defineEnum("blurQuality", BlurQuality.MEDIUM);
        ENABLE_REFRACTION = builder
                .comment("Bend the backdrop near panel edges like real glass.")
                .define("enableRefraction", true);
        builder.pop();

        builder.push("intro");
        INTRO_MODE = builder
                .comment("When to replace the startup loading screen with the Liquid Glass intro. OFF keeps the NeoForge/vanilla screen.")
                .defineEnum("introMode", IntroMode.ALWAYS);
        STYLE_RELOAD_OVERLAY = builder
                .comment("Also use the glass loading overlay for later resource reloads (F3+T, resource pack changes).")
                .define("styleReloadOverlay", true);
        DARK_EARLY_WINDOW = builder
                .comment("After the intro played, enable vanilla's dark loading screen option (darkMojangStudiosBackground) so the",
                        "NeoForge early loading window starts dark on the next launch and blends into the intro instead of flashing red.")
                .define("darkEarlyWindow", true);
        builder.pop();

        builder.push("menu");
        CUSTOM_TITLE_SCREEN = builder
                .comment("Restyle the title screen: Liquid Glass logo, buttons arranged on a glass card, entrance animation.")
                .define("customTitleScreen", true);
        GLASS_BUTTONS = builder
                .comment("Draw buttons on every screen as liquid glass instead of the vanilla texture.")
                .define("glassButtons", true);
        CAVE_BACKGROUND = builder
                .comment("Replace the menu panorama with a 3D cave: a flight in at startup, a viewpoint per menu, a dive when joining.")
                .define("caveBackground", true);
        SCREEN_FADE = builder
                .comment("Cross-fade from the previous screen when switching between menus.")
                .define("screenFade", true);
        builder.pop();

        SPEC = builder.build();
    }

    private LiquidGlassConfig() {
    }

    public enum IntroMode {
        ALWAYS,
        FIRST_LAUNCH_ONLY,
        OFF
    }

    public enum BlurQuality {
        OFF(0),
        LOW(2),
        MEDIUM(3),
        HIGH(4);

        private final int passes;

        BlurQuality(int passes) {
            this.passes = passes;
        }

        public int passes() {
            return passes;
        }
    }
}
