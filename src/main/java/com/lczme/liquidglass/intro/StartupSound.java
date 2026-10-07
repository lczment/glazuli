package com.lczme.liquidglass.intro;

import com.lczme.liquidglass.LiquidGlass;
import com.lczme.liquidglass.anim.AnimClock;
import com.lczme.liquidglass.config.LiquidGlassConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.fml.loading.FMLPaths;

/**
 * A sting when the title screen first appears, before the menu music: {@code config/liquidglass/startup.ogg} if
 * present, otherwise a short chord made of vanilla sounds. The menu music is held back until it has finished.
 */
public final class StartupSound {
    public static final ResourceLocation CUSTOM = LiquidGlass.id("startup.custom");
    /** Silence between the sting and the music. */
    private static final double MUSIC_GAP_SECONDS = 1.0;
    /** The music starts anyway if the sting is still reported as playing after this long. */
    private static final double LIMIT_SECONDS = 20.0;

    private record Note(double at, SoundEvent sound, float volume, float pitch) {
        Note(double at, Holder<SoundEvent> sound, float volume, float pitch) {
            this(at, sound.value(), volume, pitch);
        }
    }

    private static final List<Note> CHORD = List.of(
            new Note(0.00, SoundEvents.BEACON_ACTIVATE, 0.45F, 1.25F),
            new Note(0.30, SoundEvents.NOTE_BLOCK_HARP, 0.70F, 0.50F),
            new Note(0.36, SoundEvents.NOTE_BLOCK_HARP, 0.70F, 0.63F),
            new Note(0.42, SoundEvents.NOTE_BLOCK_HARP, 0.70F, 0.75F),
            new Note(0.48, SoundEvents.NOTE_BLOCK_HARP, 0.70F, 1.00F),
            new Note(0.56, SoundEvents.NOTE_BLOCK_BELL, 0.80F, 1.00F),
            new Note(0.56, SoundEvents.NOTE_BLOCK_CHIME, 0.55F, 1.50F),
            new Note(0.56, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.60F, 1.00F));

    private enum State { WAITING, PLAYING, GAP, DONE }

    private static State state = State.WAITING;
    private static double stateStart;
    private static List<Note> pending = List.of();
    private static final List<SoundInstance> playing = new ArrayList<>();

    private StartupSound() {
    }

    public static Path customFile() {
        return FMLPaths.CONFIGDIR.get().resolve(LiquidGlass.MODID).resolve("startup.ogg");
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
                if (LiquidGlassConfig.SPEC.isLoaded() && !LiquidGlassConfig.STARTUP_SOUND.get() || minecraft.level != null) {
                    // Turned off, or straight into a world (quick play): only the music, as usual.
                    state = State.DONE;
                } else if (IntroState.isDone() && minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                    start(minecraft, now);
                }
            }
            case PLAYING -> {
                while (!pending.isEmpty() && now - stateStart >= pending.getFirst().at()) {
                    Note note = pending.getFirst();
                    pending = pending.subList(1, pending.size());
                    play(minecraft, SimpleSoundInstance.forUI(note.sound(), note.pitch(), note.volume()));
                }
                playing.removeIf(sound -> !minecraft.getSoundManager().isActive(sound));
                if (pending.isEmpty() && playing.isEmpty() || now - stateStart > LIMIT_SECONDS) {
                    setState(State.GAP, now);
                }
            }
            case GAP -> {
                if (now - stateStart >= MUSIC_GAP_SECONDS) {
                    setState(State.DONE, now);
                    LiquidGlass.LOGGER.info("Startup sound finished, starting the menu music");
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
        if (Files.isRegularFile(customFile())) {
            LiquidGlass.LOGGER.info("Startup sound: {}", customFile());
            pending = List.of();
            play(minecraft, SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(CUSTOM), 1.0F, 1.0F));
        } else {
            LiquidGlass.LOGGER.info("Startup sound: built-in chord");
            pending = CHORD;
        }
    }

    private static void play(Minecraft minecraft, SoundInstance sound) {
        minecraft.getSoundManager().play(sound);
        playing.add(sound);
    }

    private static void setState(State next, double now) {
        state = next;
        stateStart = now;
    }
}
