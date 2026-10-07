package com.lczme.glazuli.config;

import com.lczme.glazuli.cave.ShaderCave;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

public final class GlazuliConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_GLASS;
    public static final ModConfigSpec.EnumValue<BlurQuality> BLUR_QUALITY;
    public static final ModConfigSpec.BooleanValue ENABLE_REFRACTION;
    public static final ModConfigSpec.EnumValue<IntroMode> INTRO_MODE;
    public static final ModConfigSpec.BooleanValue STYLE_RELOAD_OVERLAY;
    public static final ModConfigSpec.BooleanValue STARTUP_SOUND;
    public static final ModConfigSpec.BooleanValue CLASSIC_UI;
    public static final ModConfigSpec.EnumValue<InGameUi> IN_GAME_UI;
    public static final ModConfigSpec.BooleanValue CUSTOM_TITLE_SCREEN;
    public static final ModConfigSpec.BooleanValue GLASS_BUTTONS;
    public static final ModConfigSpec.BooleanValue SCREEN_FADE;
    public static final ModConfigSpec.BooleanValue MENU_BLUR_DEFAULTED;
    public static final ModConfigSpec.BooleanValue CAVE_BACKGROUND;
    public static final ModConfigSpec.ConfigValue<String> MENU_SCENE;
    public static final ModConfigSpec.BooleanValue REAL_TIME_LIGHTING;
    public static final ModConfigSpec.BooleanValue CUSTOM_FONT;
    public static final ModConfigSpec.EnumValue<FontWeight> FONT_WEIGHT;
    public static final ModConfigSpec.BooleanValue GLASS_INVENTORY;
    public static final ModConfigSpec.BooleanValue GLASS_SODIUM_SCREEN;
    public static final ModConfigSpec.BooleanValue GLASS_JEI;

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
                .comment("When to replace the startup loading screen with the Glazuli intro. OFF keeps the NeoForge/vanilla screen.")
                .defineEnum("introMode", IntroMode.ALWAYS);
        STYLE_RELOAD_OVERLAY = builder
                .comment("Also use the Mojang Studios screen with the fly-through exit for later resource reloads (resource packs, language, F3+T).")
                .define("styleReloadOverlay", true);
        STARTUP_SOUND = builder
                .comment("Play a sting when the title screen first appears and start the menu music after it.",
                        "Uses config/glazuli/startup.ogg (Ogg Vorbis) if present, otherwise an ambient take on the opening of C418's \"Minecraft\".")
                .define("startupSound", true);
        builder.pop();

        builder.push("menu");
        CLASSIC_UI = builder
                .comment("Classic mode: every screen, the font and the menu transitions go back to vanilla; only the cave background stays.",
                        "Also toggled by the button in the title screen's top-right corner.")
                .define("classicUi", false);
        IN_GAME_UI = builder
                .comment("Style while in a world (pause menu, inventories, chat, hotbar, tooltips): SAME_AS_MENU follows classicUi,",
                        "LIQUID_GLASS or VANILLA override it. Also toggled by the button in the pause menu's top-right corner.")
                .defineEnum("inGameUi", InGameUi.SAME_AS_MENU);
        CUSTOM_TITLE_SCREEN = builder
                .comment("Restyle the title screen: Glazuli logo, buttons arranged on a glass card, entrance animation.")
                .define("customTitleScreen", true);
        GLASS_BUTTONS = builder
                .comment("Draw buttons on every screen as liquid glass instead of the vanilla texture.")
                .define("glassButtons", true);
        CAVE_BACKGROUND = builder
                .comment("Replace the menu panorama with a 3D cave: a flight in at startup, a viewpoint per menu, a dive when joining.")
                .define("caveBackground", true);
        MENU_SCENE = builder
                .comment("The 3D menu scene: cave, lush, deep_dark, ice, nether, ocean, or custom:<name> for a scene saved in a world",
                        "with /menuscene save <name> (stored in config/glazuli/scenes). Also chosen with the title screen's Scenes button.")
                .define("scene", "cave");
        REAL_TIME_LIGHTING = builder
                .comment("Outdoor menu scenes follow the computer's clock: sunrise at 6:00, noon, sunset at 18:00, night.",
                        "When off, every scene keeps its own time of day.")
                .define("realTimeLighting", true);
        GLASS_INVENTORY = builder
                .comment("Draw inventories, containers (chests, furnaces, modded ones using the vanilla palette) and the hotbar as liquid glass.")
                .define("glassInventory", true);
        SCREEN_FADE = builder
                .comment("Cross-fade from the previous screen when switching between menus.")
                .define("screenFade", true);
        MENU_BLUR_DEFAULTED = builder
                .comment("Set once the vanilla Menu Background Blur option (Accessibility Settings) has been turned down to 0 so the",
                        "menu scene stays sharp. Raise that slider again for blurred menus; set this to false to reset it to 0 on next launch.")
                .define("menuBlurDefaulted", false);
        builder.pop();

        builder.push("font");
        CUSTOM_FONT = builder
                .comment("Replace the pixel font with HarmonyOS Sans (bundled; by Huawei Device Co., Ltd., see META-INF/licenses).",
                        "PingFang is used instead when installed on the system, and any .ttf/.otf/.ttc placed in",
                        "config/glazuli/fonts takes precedence over both.")
                .define("customFont", true);
        FONT_WEIGHT = builder
                .comment("Weight of the custom font. BOLD uses PingFang Semibold/Bold or HarmonyOS Sans Bold.")
                .defineEnum("fontWeight", FontWeight.BOLD);
        builder.pop();

        builder.push("compat");
        GLASS_SODIUM_SCREEN = builder
                .comment("With Sodium installed, replace its video settings with a glass version of Reese's Sodium Options.")
                .define("glassSodiumScreen", true);
        GLASS_JEI = builder
                .comment("Draw JEI's panels, buttons and search field as liquid glass.")
                .define("glassJei", true);
        builder.pop();

        SPEC = builder.build();
    }

    private GlazuliConfig() {
    }

    /** Vanilla look for the current context: {@link #IN_GAME_UI} while in a world, {@link #CLASSIC_UI} in the menus. */
    public static boolean isClassic() {
        if (!SPEC.isLoaded()) {
            return false;
        }
        if (inWorld()) {
            InGameUi inGame = IN_GAME_UI.get();
            if (inGame != InGameUi.SAME_AS_MENU) {
                return inGame == InGameUi.VANILLA;
            }
        }
        return CLASSIC_UI.get();
    }

    private static boolean inWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.level != null && !ShaderCave.isSceneLevel(minecraft.level);
    }

    public static boolean realTimeLighting() {
        return !SPEC.isLoaded() || REAL_TIME_LIGHTING.get();
    }

    public enum InGameUi implements TranslatableEnum {
        SAME_AS_MENU,
        LIQUID_GLASS,
        VANILLA;

        @Override
        public Component getTranslatedName() {
            return Component.translatable("glazuli.configuration.inGameUi." + name().toLowerCase(Locale.ROOT));
        }
    }

    public enum FontWeight {
        REGULAR("regular", "PingFangSC-Regular", "regular"),
        MEDIUM("medium", "PingFangSC-Medium", "medium"),
        BOLD("bold", "PingFangSC-Semibold", "bold", "semibold");

        private final String bundled;
        private final String pingFangFace;
        private final String[] fileWords;

        FontWeight(String bundled, String pingFangFace, String... fileWords) {
            this.bundled = bundled;
            this.pingFangFace = pingFangFace;
            this.fileWords = fileWords;
        }

        /** Suffix of the bundled {@code harmonyos_sans_sc_*.ttf}. */
        public String bundled() {
            return bundled;
        }

        /** PostScript name of the face to pick from PingFang.ttc. */
        public String pingFangFace() {
            return pingFangFace;
        }

        /** Words identifying this weight in per-weight PingFang file names. */
        public String[] fileWords() {
            return fileWords;
        }
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
