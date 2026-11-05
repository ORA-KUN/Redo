package fr.orakun.redo.core.timeline;

import fr.orakun.redo.core.json.Json;
import fr.orakun.redo.core.json.JsonSerializable;

import java.util.Map;

/** A named point in time (replay time for recording markers, output time for timeline markers). */
public final class Marker implements JsonSerializable {
    public long timeMs;
    public String label;
    /** "manual", "combat", "kill", "decor"... */
    public String kind;

    public Marker(long timeMs, String label, String kind) {
        this.timeMs = timeMs;
        this.label = label;
        this.kind = kind;
    }

    @Override
    public Object toJson() {
        Map<String, Object> m = Json.obj();
        m.put("t", timeMs);
        m.put("label", label);
        m.put("kind", kind);
        return m;
    }

    public static Marker fromJson(Map<String, Object> m) {
        return new Marker(Json.getLong(m, "t", 0), Json.getString(m, "label", ""), Json.getString(m, "kind", "manual"));
    }
}
