package com.lczme.liquidglass.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class LiquidGlassConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_GLASS;
    public static final ModConfigSpec.EnumValue<BlurQuality> BLUR_QUALITY;
    public static final ModConfigSpec.BooleanValue ENABLE_REFRACTION;
    public static final ModConfigSpec.BooleanValue ENABLE_CHROMATIC_ABERRATION;

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
        ENABLE_CHROMATIC_ABERRATION = builder
                .comment("Split color channels slightly at panel edges.")
                .define("enableChromaticAberration", true);
        builder.pop();

        SPEC = builder.build();
    }

    private LiquidGlassConfig() {
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
