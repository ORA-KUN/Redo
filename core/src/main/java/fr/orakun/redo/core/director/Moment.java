package fr.orakun.redo.core.director;

import fr.orakun.redo.core.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** An interesting stretch of the recording found by the {@link MomentDetector}. */
public final class Moment {
    public enum Kind {
        COMBAT("combat"), KILL("kill"), CHASE("course"), BUILD("construction"), SCENERY("décor"),
        GROUP("groupe"), MARKER("marqueur"), START("ouverture"), EXPLOSION("explosion");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    public final Kind kind;
    public int startMs;
    public int endMs;
    public int peakMs;
    public final List<Integer> subjects = new ArrayList<Integer>();
    public Vec3d focus;
    public double score;
    public String label;

    public Moment(Kind kind, int startMs, int endMs, int peakMs, Vec3d focus, double score, String label) {
        this.kind = kind;
        this.startMs = startMs;
        this.endMs = endMs;
        this.peakMs = peakMs;
        this.focus = focus;
        this.score = score;
        this.label = label;
    }

    public int mainSubject() {
        return subjects.isEmpty() ? Integer.MIN_VALUE : subjects.get(0);
    }

    public int duration() {
        return endMs - startMs;
    }

    public boolean overlaps(Moment o) {
        return startMs < o.endMs && o.startMs < endMs;
    }
}
