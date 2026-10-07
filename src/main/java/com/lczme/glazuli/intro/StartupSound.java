package com.lczme.glazuli.intro;

import com.lczme.glazuli.Glazuli;
import com.lczme.glazuli.anim.AnimClock;
import com.lczme.glazuli.config.GlazuliConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.fml.loading.FMLPaths;

/**
 * A sting when the title screen first appears, before the menu music: {@code config/glazuli/startup.ogg} if
 * present, otherwise the built-in one, an ambient take on the opening of C418's "Minecraft". The menu music is held
 * back until it has finished.
 */
public final class StartupSound {
    public static final ResourceLocation CUSTOM = Glazuli.id("startup.custom");
    private static final ResourceLocation BUILT_IN = Glazuli.id("startup.theme");
    /** Silence between the sting and the music. */
    private static final double MUSIC_GAP_SECONDS = 1.0;
    /** The music starts anyway if the sting is still reported as playing after this long. */
    private static final double LIMIT_SECONDS = 20.0;

    private enum State { WAITING, PLAYING, GAP, DONE }

    private static State state = State.WAITING;
    private static double stateStart;
    @Nullable
    private static SoundInstance playing;

    private StartupSound() {
    }

    public static Path customFile() {
        return FMLPaths.CONFIGDIR.get().resolve(Glazuli.MODID).resolve("startup.ogg");
    }

    /** Whether vanilla's music manager must stay quiet this tick. */
    public static boolean holdsMusic() {
        return state != State.DONE;
    }

    public static void onFrameStart() {
        if (state == State.DONE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double now = AnimClock.now();
        switch (state) {
            case WAITING -> {
                if (GlazuliConfig.SPEC.isLoaded() && !GlazuliConfig.STARTUP_SOUND.get() || minecraft.level != null) {
                    // Turned off, or straight into a world (quick play): only the music, as usual.
                    state = State.DONE;
                } else if (IntroState.isDone() && minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                    start(minecraft, now);
                }
            }
            case PLAYING -> {
                if (playing == null || !minecraft.getSoundManager().isActive(playing) || now - stateStart > LIMIT_SECONDS) {
                    playing = null;
                    setState(State.GAP, now);
                }
            }
            case GAP -> {
                if (now - stateStart >= MUSIC_GAP_SECONDS) {
                    setState(State.DONE, now);
                    Glazuli.LOGGER.info("Startup sound finished, starting the menu music");
                    if (minecraft.level == null) {
                        minecraft.getMusicManager().startPlaying(minecraft.getSituationalMusic());
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void start(Minecraft minecraft, double now) {
        setState(State.PLAYING, now);
        boolean custom = Files.isRegularFile(customFile());
        Glazuli.LOGGER.info("Startup sound: {}", custom ? customFile() : "built-in");
        playing = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(custom ? CUSTOM : BUILT_IN), 1.0F, 1.0F);
        minecraft.getSoundManager().play(playing);
    }

    private static void setState(State next, double now) {
        state = next;
        stateStart = now;
    }
}
