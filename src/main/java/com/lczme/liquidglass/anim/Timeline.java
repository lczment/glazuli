package com.lczme.liquidglass.anim;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

/**
 * Choreographs several animated values on one clock, e.g. an intro sequence:
 * <pre>{@code
 * Timeline tl = new Timeline();
 * Timeline.Track alpha = tl.track(0.0F).to(0.0F, 0.6F, 1.0F, Easing.OUT_CUBIC);
 * Timeline.Track scale = tl.track(0.85F).to(0.1F, 0.8F, 1.0F, Easing.OUT_BACK);
 * tl.start();
 * // every frame: alpha.value(), scale.value(), tl.isFinished()
 * }</pre>
 */
public final class Timeline {
    private final List<Track> tracks = new ArrayList<>();
    private double startTime = Double.NaN;
    private float length;

    public Track track(float initial) {
        Track track = new Track(initial);
        tracks.add(track);
        return track;
    }

    public Timeline start() {
        startTime = AnimClock.now();
        return this;
    }

    public boolean isStarted() {
        return !Double.isNaN(startTime);
    }

    /** Jumps to {@code seconds} into the timeline, starting it if needed. */
    public void seek(float seconds) {
        startTime = AnimClock.now() - seconds;
    }

    public void skipToEnd() {
        seek(length);
    }

    /** Seconds since start; 0 before {@link #start()}. */
    public float elapsed() {
        return isStarted() ? (float) (AnimClock.now() - startTime) : 0.0F;
    }

    public float length() {
        return length;
    }

    public boolean isFinished() {
        return isStarted() && elapsed() >= length;
    }

    public final class Track {
        private final float initial;
        private final List<Segment> segments = new ArrayList<>();

        private Track(float initial) {
            this.initial = initial;
        }

        /**
         * Animates from the previous segment's end value (or the initial value) to {@code target}.
         * Segments must be added in chronological order and must not overlap.
         */
        public Track to(float at, float duration, float target, Easing easing) {
            float from = segments.isEmpty() ? initial : segments.getLast().to;
            if (!segments.isEmpty() && at < segments.getLast().end()) {
                throw new IllegalArgumentException("Segment at " + at + "s overlaps the previous one");
            }
            segments.add(new Segment(at, duration, from, target, easing));
            length = Math.max(length, at + duration);
            return this;
        }

        public float value() {
            float t = elapsed();
            float current = initial;
            for (Segment segment : segments) {
                if (t < segment.at) {
                    break;
                }
                if (t < segment.end() && segment.duration > 0.0F) {
                    return Mth.lerp(segment.easing.apply((t - segment.at) / segment.duration), segment.from, segment.to);
                }
                current = segment.to;
            }
            return current;
        }
    }

    private record Segment(float at, float duration, float from, float to, Easing easing) {
        float end() {
            return at + duration;
        }
    }
}
