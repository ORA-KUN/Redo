package fr.orakun.redo.core.math;

/**
 * Centripetal Catmull-Rom interpolation. Centripetal parameterisation avoids the loops
 * and overshoots of the uniform version when keyframes are unevenly spaced, which keeps
 * camera moves clean.
 */
public final class Spline {
    private Spline() {
    }

    /** Interpolates between p1 and p2 (t in [0,1]) with p0 and p3 as neighbours. */
    public static Vec3d catmullRom(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double alpha = 0.5;
        double t0 = 0;
        double t1 = t0 + knot(p0, p1, alpha);
        double t2 = t1 + knot(p1, p2, alpha);
        double t3 = t2 + knot(p2, p3, alpha);
        if (t2 - t1 < 1e-9) return p1.lerp(p2, t);
        double u = t1 + (t2 - t1) * t;

        Vec3d a1 = blend(p0, p1, t0, t1, u);
        Vec3d a2 = blend(p1, p2, t1, t2, u);
        Vec3d a3 = blend(p2, p3, t2, t3, u);
        Vec3d b1 = blend(a1, a2, t0, t2, u);
        Vec3d b2 = blend(a2, a3, t1, t3, u);
        return blend(b1, b2, t1, t2, u);
    }

    private static double knot(Vec3d a, Vec3d b, double alpha) {
        double d = a.distance(b);
        return Math.max(1e-4, Math.pow(d, alpha));
    }

    private static Vec3d blend(Vec3d a, Vec3d b, double ta, double tb, double u) {
        double span = tb - ta;
        if (Math.abs(span) < 1e-9) return a;
        double wa = (tb - u) / span;
        double wb = (u - ta) / span;
        return new Vec3d(a.x * wa + b.x * wb, a.y * wa + b.y * wb, a.z * wa + b.z * wb);
    }

    /** Scalar Catmull-Rom (uniform), used for angles and fov. */
    public static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}
