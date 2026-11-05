package fr.orakun.redo.core.director;

import fr.orakun.redo.core.json.Json;
import fr.orakun.redo.core.json.JsonSerializable;
import fr.orakun.redo.core.timeline.Clip;

import java.util.Map;

/** A ready-made shot proposed in the bin, built by the director or from a prompt. */
public final class ShotSuggestion implements JsonSerializable {
    public Clip clip;
    public double score;
    public String description;
    public String momentKind;
    /** "auto", "prompt" or "manual". */
    public String origin = "auto";
    public boolean favorite;
    /** The place of the scenery the shot films, when a prompt named one; not saved. */
    public transient Landmark place;

    public ShotSuggestion(Clip clip, double score, String description, String momentKind) {
        this.clip = clip;
        this.score = score;
        this.description = description;
        this.momentKind = momentKind;
    }

    public ShotType type() {
        return ShotType.byName(clip.shotType);
    }

    @Override
    public Object toJson() {
        Map<String, Object> m = Json.obj();
        m.put("clip", clip.toJson());
        m.put("score", score);
        m.put("description", description);
        m.put("moment", momentKind);
        m.put("origin", origin);
        m.put("favorite", favorite);
        return m;
    }

    public static ShotSuggestion fromJson(Map<String, Object> m) {
        Map<String, Object> c = Json.getObj(m, "clip");
        ShotSuggestion s = new ShotSuggestion(c == null ? new Clip() : Clip.fromJson(c), Json.getDouble(m, "score", 0),
                Json.getString(m, "description", ""), Json.getString(m, "moment", ""));
        s.origin = Json.getString(m, "origin", "auto");
        s.favorite = Json.getBool(m, "favorite", false);
        return s;
    }
}
