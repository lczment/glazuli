package com.lczme.glazuli.cave;

import com.lczme.glazuli.config.GlazuliConfig;
import java.time.LocalTime;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;

/**
 * Real-time lighting for outdoor overworld scenes, like a dynamic desktop wallpaper: the computer's clock picks the
 * time of day (6:00 sunrise, 18:00 sunset), and the plain mesh's sky light and sky colour follow the sun.
 */
public final class SceneClock {
    private static final double SECONDS_PER_TICK = 86400.0 / 24000.0;
    private static final int DAY_SKY = 0xBCD6F2;
    private static final int NIGHT_SKY = 0x0A1022;
    private static final int SUNSET_SKY = 0xF0A060;
    private static final float NIGHT_SKY_LIGHT = 0.10F;

    @Nullable
    private static LocalTime override;

    private SceneClock() {
    }

    /** Development: pins the clock (null follows the real one). */
    public static void override(@Nullable LocalTime time) {
        override = time;
    }

    static boolean follows(CaveWorld world) {
        return GlazuliConfig.realTimeLighting() && world.open && !world.voidOutside
                && world.look().dimension() == SceneLook.Dimension.OVERWORLD;
    }

    static long dayTime(CaveWorld world) {
        if (!follows(world)) {
            return world.look().dayTime();
        }
        LocalTime time = override != null ? override : LocalTime.now();
        long tick = Math.round((time.toSecondOfDay() + time.getNano() / 1.0E9) / SECONDS_PER_TICK);
        // Minecraft's day starts at 6:00.
        return Math.floorMod(tick - 6000L, 24000L);
    }

    /** The scene's look at the current time; unchanged for scenes that keep their own time. */
    static SceneLook look(CaveWorld world) {
        SceneLook look = world.look();
        if (!follows(world)) {
            return look;
        }
        float sun = sunHeight(dayTime(world));
        float day = Mth.clamp(sun * 2.0F + 0.5F, 0.0F, 1.0F);
        float horizon = Mth.clamp(1.0F - Math.abs(sun) / 0.35F, 0.0F, 1.0F);
        int sky = lerpColor(lerpColor(NIGHT_SKY, DAY_SKY, day), SUNSET_SKY, horizon * 0.65F);
        float skyLight = Mth.lerp(day, NIGHT_SKY_LIGHT, 1.0F);
        return look.at(sky, skyLight);
    }

    /** Cosine of the sun's angle from the zenith (1 noon, 0 horizon, -1 midnight), as the sky renderer places it. */
    private static float sunHeight(long dayTime) {
        double d = Mth.frac(dayTime / 24000.0 - 0.25);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        double angle = (d * 2.0 + e) / 3.0;
        return (float) Math.cos(angle * Math.PI * 2.0);
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return r << 16 | g << 8 | b;
    }
}
