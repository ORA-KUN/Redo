package fr.orakun.redo.core.director;

import fr.orakun.redo.core.math.Vec3d;

import java.util.Locale;

/** Something of the scenery a shot can film: a tree, a building, a fountain, a portal... */
public final class Landmark {
    public enum Kind {
        TREE("arbre"), BUILDING("bâtiment"), TOWER("tour"), WALL("mur"), MONUMENT("monument"), FOUNTAIN("fontaine"),
        WATER("eau"), LAVA("lave"), PORTAL("portail"), BLOCK("objet");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** Id used by the prompt ("p3"). */
    public String id;
    public final Kind kind;
    /** French description, "Grand chêne (14 blocs)". */
    public String label;
    /** Centre of the base, on the ground. */
    public final Vec3d base;
    /** Horizontal half size and height, in blocks. */
    public final double radius, height;
    /** Number of blocks or columns it is made of, to rank sizes. */
    public final int size;
    /** Main material or species, or null. */
    public String material;
    /** Distance and compass direction ("nord-est") from the centre of the place. */
    public double distanceToCentre;
    public String direction = "";
    /** 1 for the biggest of its kind, 2 for the next... */
    public int rank;

    public Landmark(Kind kind, String label, Vec3d base, double radius, double height, int size) {
        this.kind = kind;
        this.label = label;
        this.base = base;
        this.radius = radius;
        this.height = height;
        this.size = size;
    }

    /** The point a camera looks at: the middle of its height. */
    public Vec3d centre() {
        return base.add(0, height * 0.5, 0);
    }

    public String describe() {
        return String.format(Locale.ROOT, "%s | %s | %s | x %d y %d z %d | %d blocs de large, %d de haut | %s",
                id, kind.name(), label, (int) Math.floor(base.x), (int) Math.floor(base.y), (int) Math.floor(base.z),
                (int) Math.round(radius * 2), (int) Math.round(height),
                distanceToCentre < 4 ? "au centre" : String.format(Locale.ROOT, "%d blocs %s du centre", (int) Math.round(distanceToCentre), where()));
    }

    /** "au nord", "à l'est"... */
    public String where() {
        return direction.startsWith("est") || direction.startsWith("ouest") ? "à l'" + direction : "au " + direction;
    }

    @Override
    public String toString() {
        return describe();
    }
}
