package fr.orakun.redo.core.director;

import fr.orakun.redo.core.camera.CameraKeyframe;
import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.format.RedoMeta;
import fr.orakun.redo.core.math.Easing;
import fr.orakun.redo.core.timeline.Clip;
import fr.orakun.redo.core.track.EventTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** The automatic director: moments in, a bin of ready-made shots out. */
public final class Director {
    public static final int MAX_SUGGESTIONS = 40;

    private final EventTrack track;
    private final RedoMeta meta;
    private final WorldQuery world;
    private final ShotBuilder builder;
    private List<Moment> moments;

    public Director(EventTrack track, RedoMeta meta, WorldQuery world) {
        this.track = track;
        this.meta = meta;
        this.world = world;
        this.builder = new ShotBuilder(track, world);
    }

    public EventTrack track() {
        return track;
    }

    public List<Moment> moments() {
        if (moments == null) moments = new MomentDetector(track, meta).detect();
        return moments;
    }

    /** Shot types that suit each kind of moment, best first. */
    public static ShotType[] fitting(Moment.Kind kind) {
        switch (kind) {
            case COMBAT: return new ShotType[]{ShotType.ORBIT, ShotType.OVER_SHOULDER, ShotType.DUTCH, ShotType.LOW_ANGLE, ShotType.TRUCK, ShotType.HIGH_ANGLE};
            case KILL: return new ShotType[]{ShotType.SLOW_MOTION, ShotType.HIGH_ANGLE, ShotType.LOW_ANGLE, ShotType.CLOSE_UP, ShotType.ORBIT};
            case CHASE: return new ShotType[]{ShotType.TRUCK, ShotType.FOLLOW, ShotType.DRONE, ShotType.DOLLY_OUT, ShotType.POV};
            case BUILD: return new ShotType[]{ShotType.TIMELAPSE, ShotType.CRANE, ShotType.DOLLY_OUT, ShotType.ORBIT};
            case SCENERY: return new ShotType[]{ShotType.ESTABLISHING, ShotType.DRONE, ShotType.PAN, ShotType.CRANE, ShotType.DOLLY_OUT};
            case GROUP: return new ShotType[]{ShotType.ESTABLISHING, ShotType.ORBIT, ShotType.CRANE, ShotType.PAN};
            case MARKER: return new ShotType[]{ShotType.ORBIT, ShotType.DOLLY_IN, ShotType.CRANE, ShotType.LOW_ANGLE, ShotType.CLOSE_UP};
            case EXPLOSION: return new ShotType[]{ShotType.ESTABLISHING, ShotType.SLOW_MOTION, ShotType.HIGH_ANGLE};
            case START:
            default: return new ShotType[]{ShotType.ESTABLISHING, ShotType.DRONE, ShotType.CRANE};
        }
    }

    public List<ShotSuggestion> analyse(long seed) {
        Random rng = new Random(seed);
        List<ShotSuggestion> out = new ArrayList<ShotSuggestion>();
        Map<ShotType, Integer> usage = new EnumMap<ShotType, Integer>(ShotType.class);
        List<Moment> ms = moments();
        int n = 0;
        for (Moment m : ms) {
            if (n++ >= 25) break;
            ShotType[] fit = fitting(m.kind);
            List<ShotType> picks = pick(fit, usage, 2, rng);
            for (ShotType t : picks) {
                Clip c = builder.build(t, m, new ShotParams(), rng);
                if (c == null) continue;
                double fitness = 1.0 - indexOf(fit, t) * 0.08;
                double score = m.score * fitness * (1 - builder.lastOcclusion * 0.8);
                ShotSuggestion s = new ShotSuggestion(c, score, m.label, m.kind.label);
                out.add(s);
                Integer u = usage.get(t);
                usage.put(t, u == null ? 1 : u + 1);
            }
        }
        Collections.sort(out, new Comparator<ShotSuggestion>() {
            @Override
            public int compare(ShotSuggestion a, ShotSuggestion b) {
                return Double.compare(b.score, a.score);
            }
        });
        while (out.size() > MAX_SUGGESTIONS) out.remove(out.size() - 1);
        return out;
    }

    private static int indexOf(ShotType[] arr, ShotType t) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == t) return i;
        return arr.length;
    }

    private static List<ShotType> pick(ShotType[] fit, Map<ShotType, Integer> usage, int count, Random rng) {
        List<ShotType> out = new ArrayList<ShotType>();
        List<ShotType> pool = new ArrayList<ShotType>();
        Collections.addAll(pool, fit);
        while (out.size() < count && !pool.isEmpty()) {
            ShotType best = null;
            double bestScore = -1;
            for (int i = 0; i < pool.size(); i++) {
                ShotType t = pool.get(i);
                Integer u = usage.get(t);
                double s = (1.0 - indexOf(fit, t) * 0.12) / (1 + (u == null ? 0 : u) * 0.6) + rng.nextDouble() * 0.15;
                if (s > bestScore) {
                    bestScore = s;
                    best = t;
                }
            }
            out.add(best);
            pool.remove(best);
        }
        return out;
    }

    /** Builds one shot on demand (used by the prompt and by the "Nouveau plan" button). */
    public ShotSuggestion generate(ShotType type, Moment m, ShotParams p, long seed) {
        Clip c = builder.build(type, m, p, new Random(seed));
        if (c == null) return null;
        ShotSuggestion s = new ShotSuggestion(c, m.score * (1 - builder.lastOcclusion * 0.8), m.label, m.kind.label);
        s.origin = "prompt";
        return s;
    }

    /** Finds the best moment of a kind (or any kind when null), optionally involving a subject. */
    public Moment bestMoment(Moment.Kind kind, int subject) {
        for (Moment m : moments()) {
            if (kind != null && m.kind != kind) continue;
            if (subject != Integer.MIN_VALUE && !m.subjects.contains(subject)) continue;
            return m;
        }
        return null;
    }

    /** A synthetic moment around an arbitrary replay time, centred on a subject when possible. */
    public Moment momentAt(long timeMs, int subject) {
        int s = subject != Integer.MIN_VALUE ? subject : track.selfId();
        fr.orakun.redo.core.math.Vec3d p = s != Integer.MIN_VALUE ? track.positionAt(s, timeMs) : null;
        Moment m = new Moment(Moment.Kind.MARKER, (int) Math.max(0, timeMs - 2000), (int) timeMs + 4000, (int) timeMs, p, 0.5,
                "À " + formatTime(timeMs));
        if (p != null) m.subjects.add(s);
        return m;
    }

    public static String formatTime(long ms) {
        long s = ms / 1000;
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** Shortens a clip, adding a keyframe at the new end so the move stays the same. */
    public static void trimTo(Clip c, long duration) {
        if (duration >= c.durationMs) return;
        CameraState end = c.cameraAt(duration);
        List<CameraKeyframe> keep = new ArrayList<CameraKeyframe>();
        for (CameraKeyframe k : c.path.keys()) if (k.timeMs < duration) keep.add(k);
        c.path.keys().clear();
        c.path.keys().addAll(keep);
        if (end != null) c.path.add(new CameraKeyframe(duration, end, Easing.LINEAR));
        c.durationMs = duration;
    }
}
