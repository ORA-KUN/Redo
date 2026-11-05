package fr.orakun.redo.core.timeline;

import fr.orakun.redo.core.camera.CameraPath;
import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.json.Json;
import fr.orakun.redo.core.json.JsonSerializable;

import java.util.Map;
import java.util.UUID;

/**
 * A shot on the camera track. It shows the replay from {@code sourceStartMs} onward, played at
 * {@code speed}, during {@code durationMs} of output time, with its own camera keyframes
 * (times relative to the clip start, in output time).
 */
public final class Clip implements JsonSerializable {
    public enum Transition { CUT, FADE }

    public String id;
    public String name;
    public String shotType;
    public long timelineStartMs;
    public long durationMs;
    public long sourceStartMs;
    public double speed = 1.0;
    public CameraPath path = new CameraPath();
    public Transition transitionIn = Transition.CUT;
    public long transitionMs = 400;
    /** Optional subject entity id the shot was built around (for display only). */
    public int subjectEntity = Integer.MIN_VALUE;

    public Clip() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = "Plan";
    }

    public long timelineEndMs() {
        return timelineStartMs + durationMs;
    }

    public long sourceEndMs() {
        return sourceStartMs + Math.round(durationMs * speed);
    }

    public boolean containsTimeline(double t) {
        return t >= timelineStartMs && t < timelineEndMs();
    }

    /** Replay time (ms) shown at a local clip time (ms, output time). */
    public double replayTimeAt(double localMs) {
        return sourceStartMs + localMs * speed;
    }

    public CameraState cameraAt(double localMs) {
        return path.evaluate(localMs);
    }

    public Clip copy() {
        Clip c = fromJson(Json.parseObject(Json.write(toJson())));
        c.id = UUID.randomUUID().toString().substring(0, 8);
        return c;
    }

    @Override
    public Object toJson() {
        Map<String, Object> m = Json.obj();
        m.put("id", id);
        m.put("name", name);
        if (shotType != null) m.put("shot", shotType);
        m.put("start", timelineStartMs);
        m.put("duration", durationMs);
        m.put("source", sourceStartMs);
        m.put("speed", speed);
        m.put("transition", transitionIn.name());
        m.put("transitionMs", transitionMs);
        if (subjectEntity != Integer.MIN_VALUE) m.put("subject", subjectEntity);
        m.put("path", path.toJson());
        return m;
    }

    public static Clip fromJson(Map<String, Object> m) {
        Clip c = new Clip();
        c.id = Json.getString(m, "id", c.id);
        c.name = Json.getString(m, "name", "Plan");
        c.shotType = Json.getString(m, "shot", null);
        c.timelineStartMs = Json.getLong(m, "start", 0);
        c.durationMs = Math.max(50, Json.getLong(m, "duration", 3000));
        c.sourceStartMs = Json.getLong(m, "source", 0);
        c.speed = Json.getDouble(m, "speed", 1.0);
        c.transitionIn = "FADE".equals(Json.getString(m, "transition", "CUT")) ? Transition.FADE : Transition.CUT;
        c.transitionMs = Json.getLong(m, "transitionMs", 400);
        c.subjectEntity = Json.getInt(m, "subject", Integer.MIN_VALUE);
        c.path = CameraPath.fromJson(Json.getObj(m, "path"));
        return c;
    }
}
