package fr.orakun.redo.core.format;

import fr.orakun.redo.core.json.Json;
import fr.orakun.redo.core.json.JsonSerializable;
import fr.orakun.redo.core.timeline.Marker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Description of a recording ("meta.json"). */
public final class RedoMeta implements JsonSerializable {
    public static final int FORMAT = 1;

    public int format = FORMAT;
    public String redoVersion = "";
    public String minecraftVersion = "";
    /** "legacy" (1.8.9) or "modern" (1.20+). */
    public String family = "";
    public int protocol;
    public String serverName = "";
    public String serverAddress = "";
    public long startedAt;
    public long durationMs;
    public long packetCount;
    public int selfEntityId = Integer.MIN_VALUE;
    public String selfUuid = "";
    public String selfName = "";
    public final List<long[]> highlights = new ArrayList<long[]>();
    public final List<Marker> markers = new ArrayList<Marker>();

    @Override
    public Object toJson() {
        Map<String, Object> m = Json.obj();
        m.put("format", format);
        m.put("redo", redoVersion);
        m.put("minecraft", minecraftVersion);
        m.put("family", family);
        m.put("protocol", protocol);
        m.put("serverName", serverName);
        m.put("serverAddress", serverAddress);
        m.put("startedAt", startedAt);
        m.put("durationMs", durationMs);
        m.put("packets", packetCount);
        Map<String, Object> self = Json.obj();
        self.put("id", selfEntityId);
        self.put("uuid", selfUuid);
        self.put("name", selfName);
        m.put("self", self);
        List<Object> hl = new ArrayList<Object>();
        for (long[] h : highlights) {
            List<Object> pair = new ArrayList<Object>();
            pair.add(h[0]);
            pair.add(h[1]);
            hl.add(pair);
        }
        m.put("highlights", hl);
        List<Object> ml = new ArrayList<Object>();
        for (Marker k : markers) ml.add(k.toJson());
        m.put("markers", ml);
        return m;
    }

    public static RedoMeta fromJson(Map<String, Object> m) {
        RedoMeta r = new RedoMeta();
        r.format = Json.getInt(m, "format", FORMAT);
        r.redoVersion = Json.getString(m, "redo", "");
        r.minecraftVersion = Json.getString(m, "minecraft", "");
        r.family = Json.getString(m, "family", "");
        r.protocol = Json.getInt(m, "protocol", 0);
        r.serverName = Json.getString(m, "serverName", "");
        r.serverAddress = Json.getString(m, "serverAddress", "");
        r.startedAt = Json.getLong(m, "startedAt", 0);
        r.durationMs = Json.getLong(m, "durationMs", 0);
        r.packetCount = Json.getLong(m, "packets", 0);
        Map<String, Object> self = Json.getObj(m, "self");
        if (self != null) {
            r.selfEntityId = Json.getInt(self, "id", Integer.MIN_VALUE);
            r.selfUuid = Json.getString(self, "uuid", "");
            r.selfName = Json.getString(self, "name", "");
        }
        for (Object o : Json.getList(m, "highlights")) {
            if (o instanceof List && ((List<?>) o).size() == 2) {
                List<?> l = (List<?>) o;
                r.highlights.add(new long[]{((Number) l.get(0)).longValue(), ((Number) l.get(1)).longValue()});
            }
        }
        for (Map<String, Object> k : Json.objects(Json.getList(m, "markers"))) r.markers.add(Marker.fromJson(k));
        return r;
    }
}
