package fr.orakun.redo.core.camera;

import fr.orakun.redo.core.json.Json;
import fr.orakun.redo.core.json.JsonSerializable;
import fr.orakun.redo.core.math.Easing;

import java.util.Map;

/** A camera keyframe. {@code timeMs} is relative to the start of the clip that owns it. */
public final class CameraKeyframe implements JsonSerializable, Comparable<CameraKeyframe> {
    public long timeMs;
    public CameraState state;
    /** Easing applied to the segment that starts at this keyframe. */
    public Easing easing;

    public CameraKeyframe(long timeMs, CameraState state, Easing easing) {
        this.timeMs = timeMs;
        this.state = state;
        this.easing = easing;
    }

    public CameraKeyframe copy() {
        return new CameraKeyframe(timeMs, state, easing);
    }

    @Override
    public int compareTo(CameraKeyframe o) {
        return Long.compare(timeMs, o.timeMs);
    }

    @Override
    public Object toJson() {
        Map<String, Object> m = Json.obj();
        m.put("t", timeMs);
        m.put("cam", state.toJson());
        m.put("ease", easing.name());
        return m;
    }

    public static CameraKeyframe fromJson(Map<String, Object> m) {
        Map<String, Object> cam = Json.getObj(m, "cam");
        return new CameraKeyframe(Json.getLong(m, "t", 0),
                cam == null ? new CameraState(0, 64, 0, 0, 0, 0, CameraState.DEFAULT_FOV) : CameraState.fromJson(cam),
                Easing.byName(Json.getString(m, "ease", null), Easing.LINEAR));
    }
}
