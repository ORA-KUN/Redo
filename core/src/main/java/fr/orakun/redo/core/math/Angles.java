package fr.orakun.redo.core.math;

public final class Angles {
    private Angles() {
    }

    /** Wraps an angle to [-180, 180). */
    public static double wrap(double deg) {
        double d = deg % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    /** Returns {@code to} shifted by multiples of 360 so that it is the closest to {@code from}. */
    public static double unwrapNear(double from, double to) {
        return from + wrap(to - from);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
