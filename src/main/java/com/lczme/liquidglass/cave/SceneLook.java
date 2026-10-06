package com.lczme.liquidglass.cave;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.Mth;

/**
 * How a menu scene is coloured: the clear/fog colour and fog range, the light map (block light tint plus an ambient
 * floor so unlit rock isn't pure black, and sky light for captured outdoor scenes), the biome tints and the time of day
 * the shader world is set to.
 */
public record SceneLook(int fogColor, float fogStart, float fogEnd,
                        float blockR, float blockG, float blockB,
                        float ambientR, float ambientG, float ambientB,
                        float skyBrightness, int water, int grass, int foliage, long dayTime) {
    static final int WATER = 0x3F76E4;
    static final int GRASS = 0x79C05A;
    static final int FOLIAGE = 0x59AE30;

    static SceneLook underground(int fogColor, float fogEnd, float blockR, float blockG, float blockB,
                                 float ambientR, float ambientG, float ambientB) {
        return new SceneLook(fogColor, 12.0F, fogEnd, blockR, blockG, blockB, ambientR, ambientG, ambientB, 0.0F,
                WATER, GRASS, FOLIAGE, 6000L);
    }

    SceneLook withFog(float start, float end) {
        return new SceneLook(fogColor, start, end, blockR, blockG, blockB, ambientR, ambientG, ambientB, skyBrightness,
                water, grass, foliage, dayTime);
    }

    float red() {
        return ((fogColor >> 16) & 0xFF) / 255.0F;
    }

    float green() {
        return ((fogColor >> 8) & 0xFF) / 255.0F;
    }

    float blue() {
        return (fogColor & 0xFF) / 255.0F;
    }

    void paintLightmap(NativeImage pixels) {
        for (int block = 0; block < 16; block++) {
            float b = (float) Math.pow(block / 15.0F, 1.35) * 1.15F;
            for (int sky = 0; sky < 16; sky++) {
                float s = (float) Math.pow(sky / 15.0F, 1.6) * skyBrightness;
                int r = toByte(ambientR + blockR * b + 0.95F * s);
                int g = toByte(ambientG + blockG * b + 0.97F * s);
                int bl = toByte(ambientB + blockB * b + 1.00F * s);
                pixels.setPixelRGBA(block, sky, 0xFF000000 | bl << 16 | g << 8 | r);
            }
        }
    }

    private static int toByte(float value) {
        return Mth.clamp((int) (value * 255.0F), 0, 255);
    }
}
