package fr.orakun.redo.core.math;

/** Immutable 3D vector, independent from Minecraft's own vector classes. */
public final class Vec3d {
    public static final Vec3d ZERO = new Vec3d(0, 0, 0);

    public final double x;
    public final double y;
    public final double z;

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3d add(Vec3d o) {
        return new Vec3d(x + o.x, y + o.y, z + o.z);
    }

    public Vec3d add(double dx, double dy, double dz) {
        return new Vec3d(x + dx, y + dy, z + dz);
    }

    public Vec3d sub(Vec3d o) {
        return new Vec3d(x - o.x, y - o.y, z - o.z);
    }

    public Vec3d mul(double f) {
        return new Vec3d(x * f, y * f, z * f);
    }

    public double dot(Vec3d o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec3d cross(Vec3d o) {
        return new Vec3d(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double horizontalLength() {
        return Math.sqrt(x * x + z * z);
    }

    public double distance(Vec3d o) {
        return sub(o).length();
    }

    public double distanceSq(Vec3d o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public Vec3d normalize() {
        double l = length();
        return l < 1e-9 ? ZERO : mul(1.0 / l);
    }

    public Vec3d lerp(Vec3d o, double t) {
        return new Vec3d(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }

    /** Unit vector pointing in the direction of a Minecraft yaw/pitch (degrees). */
    public static Vec3d fromYawPitch(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch);
        return new Vec3d(-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp);
    }

    /** Minecraft yaw (degrees) that looks from this point toward {@code target}. */
    public double yawTo(Vec3d target) {
        double dx = target.x - x, dz = target.z - z;
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Minecraft pitch (degrees, positive = down) that looks from this point toward {@code target}. */
    public double pitchTo(Vec3d target) {
        double dx = target.x - x, dy = target.y - y, dz = target.z - z;
        double h = Math.sqrt(dx * dx + dz * dz);
        return Math.toDegrees(-Math.atan2(dy, h));
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", x, y, z);
    }
}
