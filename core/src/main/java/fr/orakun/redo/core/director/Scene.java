package fr.orakun.redo.core.director;

import fr.orakun.redo.core.math.Vec3d;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** What {@link SceneScanner} found around a point: the places a camera can film. */
public final class Scene {
    /** How a request picks one place among several of the same kind. */
    public enum Pick {
        /** The one nearest to the centre of the place ("l'arbre au centre du hub"). */
        CENTRAL,
        BIGGEST,
        SMALLEST,
        /** The one nearest to the recording player ("près de moi"). */
        NEAREST,
        /** Nothing said: the most telling one. */
        ANY
    }

    /** Middle of what is built around (a hub's square), else where the recording player appeared. */
    public final Vec3d centre;
    public final boolean centreFromBuildings;
    public final List<Landmark> landmarks;
    public final Vec3d around;
    public final int radius;

    public Scene(Vec3d centre, boolean centreFromBuildings, List<Landmark> landmarks, Vec3d around, int radius) {
        this.centre = centre;
        this.centreFromBuildings = centreFromBuildings;
        this.landmarks = Collections.unmodifiableList(landmarks);
        this.around = around;
        this.radius = radius;
    }

    public Landmark byId(String id) {
        if (id == null) return null;
        String k = id.trim();
        for (Landmark l : landmarks) if (l.id.equalsIgnoreCase(k)) return l;
        return null;
    }

    /**
     * The place of one of the given kinds (any kind when empty) that matches the pick, standing in
     * the given compass direction from the centre when one is given ("nord"...), or null.
     */
    public Landmark find(Set<Landmark.Kind> kinds, Pick pick, String direction, Vec3d self) {
        Landmark best = null;
        double bestScore = 0;
        for (Landmark l : landmarks) {
            if (kinds != null && !kinds.isEmpty() && !kinds.contains(l.kind)) continue;
            if (direction != null && !direction.isEmpty() && (l.distanceToCentre < 4 || !l.direction.startsWith(direction))) continue;
            double score;
            switch (pick) {
                case CENTRAL:
                    score = -l.distanceToCentre + Math.log(1 + l.height) * 0.5;
                    break;
                case BIGGEST:
                    score = l.height * (l.radius + 1);
                    break;
                case SMALLEST:
                    score = -l.height * (l.radius + 1);
                    break;
                case NEAREST:
                    score = self == null ? -l.distanceToCentre : -Math.hypot(l.base.x - self.x, l.base.z - self.z);
                    break;
                default:
                    score = -landmarks.indexOf(l);
            }
            if (best == null || score > bestScore) {
                best = l;
                bestScore = score;
            }
        }
        return best;
    }

    public static Set<Landmark.Kind> kinds(Landmark.Kind... k) {
        EnumSet<Landmark.Kind> s = EnumSet.noneOf(Landmark.Kind.class);
        Collections.addAll(s, k);
        return s;
    }

    /** Text sent to Claude with a request. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Scenery within %d blocks of the recording player (north is -z). Centre of the place: x %d z %d (%s).%n",
                radius, (int) Math.floor(centre.x), (int) Math.floor(centre.z),
                centreFromBuildings ? "middle of what is built around, a hub's square for instance" : "where the player appeared"));
        if (landmarks.isEmpty()) {
            sb.append("Places: none found.\n");
            return sb.toString();
        }
        sb.append("Places (id | kind | description | base position | size | where from the centre):\n");
        for (Landmark l : landmarks) sb.append(l.describe()).append('\n');
        return sb.toString();
    }
}
