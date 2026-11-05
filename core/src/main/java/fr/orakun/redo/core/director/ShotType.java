package fr.orakun.redo.core.director;

/** The shot library of the automatic director. */
public enum ShotType {
    ESTABLISHING("Plan large d'ouverture", Size.WIDE, 5000),
    DRONE("Survol drone", Size.WIDE, 6000),
    DOLLY_IN("Travelling avant", Size.MEDIUM, 4000),
    DOLLY_OUT("Travelling arrière", Size.MEDIUM, 4500),
    TRUCK("Travelling latéral", Size.MEDIUM, 4000),
    FOLLOW("Suivi de dos", Size.MEDIUM, 4500),
    ORBIT("Orbite", Size.MEDIUM, 5000),
    CRANE("Grue", Size.WIDE, 5000),
    LOW_ANGLE("Contre-plongée", Size.MEDIUM, 3500),
    HIGH_ANGLE("Plongée", Size.MEDIUM, 3500),
    OVER_SHOULDER("Plan épaule", Size.TIGHT, 3500),
    CLOSE_UP("Gros plan visage", Size.TIGHT, 3000),
    POV("Point de vue", Size.TIGHT, 4000),
    PAN("Panoramique", Size.WIDE, 5500),
    DUTCH("Plan penché", Size.MEDIUM, 3500),
    SLOW_MOTION("Ralenti", Size.TIGHT, 4000),
    TIMELAPSE("Timelapse", Size.WIDE, 5000);

    public enum Size { WIDE, MEDIUM, TIGHT }

    public final String label;
    public final Size size;
    public final long defaultDurationMs;

    ShotType(String label, Size size, long defaultDurationMs) {
        this.label = label;
        this.size = size;
        this.defaultDurationMs = defaultDurationMs;
    }

    public static ShotType byName(String s) {
        if (s == null) return null;
        for (ShotType t : values()) if (t.name().equalsIgnoreCase(s)) return t;
        return null;
    }

    /** Whether the shot needs a subject (an entity) rather than a place. */
    public boolean needsSubject() {
        return this != ESTABLISHING && this != DRONE && this != PAN;
    }
}
