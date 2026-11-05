package fr.orakun.redo.core.camera;

import fr.orakun.redo.core.json.Json;
import fr.orakun.redo.core.json.JsonSerializable;
import fr.orakun.redo.core.math.Vec3d;

import java.util.Map;

/** Position and orientation of the virtual camera. Angles follow Minecraft conventions (degrees). */
public final class CameraState implements JsonSerializable {
    public static final double DEFAULT_FOV = 70.0;

    public final double x, y, z;
    public final double yaw, pitch, roll;
    public final double fov;

    public CameraState(double x, double y, double z, double yaw, double pitch, double roll, double fov) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.fov = fov;
    }

    public CameraState(Vec3d pos, double yaw, double pitch) {
        this(pos.x, pos.y, pos.z, yaw, pitch, 0, DEFAULT_FOV);
    }

    public static CameraState lookAt(Vec3d eye, Vec3d target, double roll, double fov) {
        return new CameraState(eye.x, eye.y, eye.z, eye.yawTo(target), eye.pitchTo(target), roll, fov);
    }

    public Vec3d pos() {
        return new Vec3d(x, y, z);
    }

    public Vec3d forward() {
        return Vec3d.fromYawPitch(yaw, pitch);
    }

    public CameraState withPos(double nx, double ny, double nz) {
        return new CameraState(nx, ny, nz, yaw, pitch, roll, fov);
    }

    public CameraState withAngles(double nyaw, double npitch, double nroll) {
        return new CameraState(x, y, z, nyaw, npitch, nroll, fov);
    }

    public CameraState withFov(double nfov) {
        return new CameraState(x, y, z, yaw, pitch, roll, nfov);
    }

    @Override
    public Object toJson() {
        Map<String, Object> m = Json.obj();
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        m.put("yaw", yaw);
        m.put("pitch", pitch);
        if (roll != 0) m.put("roll", roll);
        m.put("fov", fov);
        return m;
    }

    public static CameraState fromJson(Map<String, Object> m) {
        return new CameraState(
                Json.getDouble(m, "x", 0), Json.getDouble(m, "y", 64), Json.getDouble(m, "z", 0),
                Json.getDouble(m, "yaw", 0), Json.getDouble(m, "pitch", 0), Json.getDouble(m, "roll", 0),
                Json.getDouble(m, "fov", DEFAULT_FOV));
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "Camera[%.1f %.1f %.1f | %.1f° %.1f° %.1f° | fov %.0f]",
                x, y, z, yaw, pitch, roll, fov);
    }
}
