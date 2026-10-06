package com.lczme.liquidglass.cave;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

/** The generated menu scenes: one cave layout, re-dressed with another palette, decorations, light and fog. */
public enum CaveTheme {
    CAVE(SceneLook.underground(0x050608, CaveCamera.FOG_END, 1.00F, 0.82F, 0.60F, 0.13F, 0.15F, 0.20F)),
    LUSH(SceneLook.underground(0x060B07, CaveCamera.FOG_END, 1.00F, 0.88F, 0.62F, 0.12F, 0.17F, 0.15F)),
    DEEP_DARK(SceneLook.underground(0x020408, 52.0F, 0.50F, 0.85F, 1.00F, 0.06F, 0.08F, 0.12F)),
    ICE(SceneLook.underground(0x0B131D, CaveCamera.FOG_END, 0.85F, 0.95F, 1.05F, 0.16F, 0.20F, 0.27F)),
    NETHER(SceneLook.underground(0x1C0604, 50.0F, 1.00F, 0.70F, 0.45F, 0.22F, 0.10F, 0.07F)),
    OCEAN(SceneLook.underground(0x06253C, 40.0F, 0.70F, 0.92F, 1.00F, 0.07F, 0.15F, 0.23F).withFog(2.0F, 40.0F));

    private final SceneLook look;

    CaveTheme(SceneLook look) {
        this.look = look;
    }

    SceneLook look() {
        return look;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("liquidglass.scene." + id());
    }

    @Nullable
    public static CaveTheme byId(String id) {
        for (CaveTheme theme : values()) {
            if (theme.id().equals(id)) {
                return theme;
            }
        }
        return null;
    }
}
