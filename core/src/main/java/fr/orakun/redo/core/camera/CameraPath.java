package fr.orakun.redo.core.camera;

import fr.orakun.redo.core.json.Json;
import fr.orakun.redo.core.json.JsonSerializable;
import fr.orakun.redo.core.math.Angles;
import fr.orakun.redo.core.math.Spline;
import fr.orakun.redo.core.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** An ordered list of camera keyframes evaluated with splines and per-segment easing. */
public final class CameraPath implements JsonSerializable {
    public enum Interpolation { SPLINE, LINEAR }

    private final List<CameraKeyframe> keys = new ArrayList<CameraKeyframe>();
    public Interpolation interpolation = Interpolation.SPLINE;

    public List<CameraKeyframe> keys() {
        return keys;
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public void add(CameraKeyframe k) {
        // replace a keyframe at the same instant
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).timeMs == k.timeMs) {
                keys.set(i, k);
                return;
            }
        }
        keys.add(k);
        Collections.sort(keys);
    }

    public void sort() {
        Collections.sort(keys);
    }

    public long duration() {
        return keys.isEmpty() ? 0 : keys.get(keys.size() - 1).timeMs;
    }

    public CameraPath copy() {
        CameraPath p = new CameraPath();
        p.interpolation = interpolation;
        for (CameraKeyframe k : keys) p.keys.add(k.copy());
        return p;
    }

    /** Shifts and scales all keyframe times (used when a clip is trimmed or stretched). */
    public void retime(double scale, long offset) {
        for (CameraKeyframe k : keys) k.timeMs = Math.round(k.timeMs * scale) + offset;
        sort();
    }

    public CameraState evaluate(double t) {
        int n = keys.size();
        if (n == 0) return null;
        if (n == 1 || t <= keys.get(0).timeMs) return keys.get(0).state;
        if (t >= keys.get(n - 1).timeMs) return keys.get(n - 1).state;

        int i = 0;
        while (i < n - 2 && keys.get(i + 1).timeMs <= t) i++;
        CameraKeyframe a = keys.get(i);
        CameraKeyframe b = keys.get(i + 1);
        double span = Math.max(1, b.timeMs - a.timeMs);
        double u = a.easing.apply((t - a.timeMs) / span);

        CameraState s0 = keys.get(Math.max(0, i - 1)).state;
        CameraState s1 = a.state;
        CameraState s2 = b.state;
        CameraState s3 = keys.get(Math.min(n - 1, i + 2)).state;

        Vec3d pos;
        if (interpolation == Interpolation.SPLINE && n > 2) {
            pos = Spline.catmullRom(s0.pos(), s1.pos(), s2.pos(), s3.pos(), u);
        } else {
            pos = s1.pos().lerp(s2.pos(), u);
        }

        // unwrap yaw so the camera always turns the short way round
        double y1 = s1.yaw;
        double y0 = Angles.unwrapNear(y1, s0.yaw);
        double y2 = Angles.unwrapNear(y1, s2.yaw);
        double y3 = Angles.unwrapNear(y2, s3.yaw);
        double yaw, pitch, roll, fov;
        if (interpolation == Interpolation.SPLINE && n > 2) {
            yaw = Spline.catmullRom(y0, y1, y2, y3, u);
            pitch = Spline.catmullRom(s0.pitch, s1.pitch, s2.pitch, s3.pitch, u);
            roll = Spline.catmullRom(s0.roll, s1.roll, s2.roll, s3.roll, u);
            fov = Spline.catmullRom(s0.fov, s1.fov, s2.fov, s3.fov, u);
        } else {
            yaw = Angles.lerp(y1, y2, u);
            pitch = Angles.lerp(s1.pitch, s2.pitch, u);
            roll = Angles.lerp(s1.roll, s2.roll, u);
            fov = Angles.lerp(s1.fov, s2.fov, u);
        }
        pitch = Angles.clamp(pitch, -90, 90);
        fov = Angles.clamp(fov, 5, 170);
        return new CameraState(pos.x, pos.y, pos.z, Angles.wrap(yaw), pitch, roll, fov);
    }

    @Override
    public Object toJson() {
        Map<String, Object> m = Json.obj();
        m.put("interp", interpolation.name());
        List<Object> l = new ArrayList<Object>();
        for (CameraKeyframe k : keys) l.add(k.toJson());
        m.put("keys", l);
        return m;
    }

    public static CameraPath fromJson(Map<String, Object> m) {
        CameraPath p = new CameraPath();
        if (m == null) return p;
        String interp = Json.getString(m, "interp", "SPLINE");
        p.interpolation = "LINEAR".equals(interp) ? Interpolation.LINEAR : Interpolation.SPLINE;
        for (Map<String, Object> k : Json.objects(Json.getList(m, "keys"))) p.keys.add(CameraKeyframe.fromJson(k));
        p.sort();
        return p;
    }
}
