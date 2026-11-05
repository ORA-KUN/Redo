package fr.orakun.redo.core.math;

/** Easing curves applied to the time parameter between two keyframes. */
public enum Easing {
    LINEAR("Linéaire"),
    EASE_IN("Accélération"),
    EASE_OUT("Freinage"),
    EASE_IN_OUT("Douce"),
    SMOOTH("Très douce"),
    HOLD("Maintien");

    public final String label;

    Easing(String label) {
        this.label = label;
    }

    public double apply(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        switch (this) {
            case EASE_IN:
                return t * t * t;
            case EASE_OUT: {
                double u = 1 - t;
                return 1 - u * u * u;
            }
            case EASE_IN_OUT:
                return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            case SMOOTH:
                // smootherstep: zero first and second derivative at both ends
                return t * t * t * (t * (t * 6 - 15) + 10);
            case HOLD:
                return 0;
            case LINEAR:
            default:
                return t;
        }
    }

    public Easing next() {
        Easing[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public static Easing byName(String name, Easing def) {
        if (name == null) return def;
        for (Easing e : values()) {
            if (e.name().equalsIgnoreCase(name)) return e;
        }
        return def;
    }
}
