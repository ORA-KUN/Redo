package fr.orakun.redo.core.prompt;

import fr.orakun.redo.core.director.Moment;
import fr.orakun.redo.core.director.ShotType;
import fr.orakun.redo.core.director.TrailerBuilder;
import fr.orakun.redo.core.json.Json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * What a prompt asks for, independent of who understood it (Claude or the offline parser):
 * a list of shots and optionally a trailer.
 */
public final class ShotRequest {
    public static final class Spec {
        /** Null lets the director choose the type that suits the moment. */
        public ShotType type;
        /** Id of a moment from the replay summary ("m3"), or null. */
        public String momentId;
        /** Kind of moment wanted when no id is given, or null. */
        public Moment.Kind kind;
        /** Player name, "self" for the recording player, or null. */
        public String subject;
        /** Replay time in seconds, or -1. */
        public double atSec = -1;
        public double durationSec = -1;
        /** Playback speed (0.25 = slow motion), NaN = default. */
        public double speed = Double.NaN;
        public double distance = 1;
        public double height = 1;
        public double motion = 1;
        public int side;
        /** Id of a place of the scenery from the summary ("p3"), or null. */
        public String placeId;
        /** A place described in words, found in the scenery when the shot is built, or null. */
        public PlaceQuery place;

        @Override
        public String toString() {
            return (type == null ? "auto" : type.name()) + (momentId != null ? " @" + momentId : "")
                    + (kind != null ? " " + kind.name() : "") + (subject != null ? " on " + subject : "")
                    + (placeId != null ? " place " + placeId : "") + (place != null ? " place " + place : "")
                    + (atSec >= 0 ? " at " + atSec + "s" : "") + (durationSec > 0 ? " for " + durationSec + "s" : "")
                    + (motion != 1 ? " motion " + motion : "");
        }
    }

    public final List<Spec> shots = new ArrayList<Spec>();
    public boolean trailer;
    public TrailerBuilder.Style style = TrailerBuilder.Style.CINEMATIC;
    /** Wanted trailer length in seconds, or -1 for the style's default. */
    public double trailerSec = -1;
    /** Short sentence shown to the user (from Claude, or built by the offline parser). */
    public String reply = "";
    /** "claude" or "offline". */
    public String source = "offline";

    public boolean isEmpty() {
        return shots.isEmpty() && !trailer;
    }

    // ------------------------------------------------------------------ Claude structured output

    private static List<Object> enumOf(Object... values) {
        return new ArrayList<Object>(Arrays.asList(values));
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> m = Json.obj();
        m.put("type", type);
        m.put("description", description);
        return m;
    }

    private static Map<String, Object> object(Map<String, Object> props) {
        Map<String, Object> m = Json.obj();
        m.put("type", "object");
        m.put("properties", props);
        m.put("required", new ArrayList<Object>(props.keySet()));
        m.put("additionalProperties", false);
        return m;
    }

    /** JSON schema of the answer expected from Claude. */
    public static Map<String, Object> schema() {
        List<Object> types = new ArrayList<Object>();
        types.add("AUTO");
        for (ShotType t : ShotType.values()) types.add(t.name());
        List<Object> kinds = new ArrayList<Object>();
        kinds.add("ANY");
        for (Moment.Kind k : Moment.Kind.values()) kinds.add(k.name());

        Map<String, Object> shot = Json.obj();
        Map<String, Object> type = prop("string", "Shot type, AUTO to let the director choose");
        type.put("enum", types);
        shot.put("type", type);
        shot.put("moment", prop("string", "Moment id from the summary (e.g. m3), or empty"));
        Map<String, Object> kind = prop("string", "Kind of moment wanted when no moment id is given");
        kind.put("enum", kinds);
        shot.put("kind", kind);
        shot.put("subject", prop("string", "Exact player name from the summary, 'self' for the recording player, or empty"));
        shot.put("place", prop("string", "Id of a place of the scenery from the summary (e.g. p3) to film it instead of a player, or empty"));
        shot.put("at_seconds", prop("number", "Replay time where the shot starts, or -1"));
        shot.put("duration_seconds", prop("number", "Shot length, or -1 for the default"));
        shot.put("speed", prop("number", "Playback speed: 1 normal, 0.25 slow motion, 4 fast; 0 for the default"));
        shot.put("distance", prop("number", "Distance multiplier: 1 default, 0.5 closer, 2 further"));
        shot.put("height", prop("number", "Height multiplier: 1 default, 0 at eye level, 3 much higher"));
        shot.put("motion", prop("number", "Camera move speed: 0.5 slow, 1 normal, 2 fast"));
        shot.put("side", prop("integer", "1 right / clockwise, -1 left / counter-clockwise, 0 any"));

        Map<String, Object> shots = Json.obj();
        shots.put("type", "array");
        shots.put("items", object(shot));
        shots.put("description", "Shots to create, in the order the user wants them");

        Map<String, Object> trailer = Json.obj();
        trailer.put("enabled", prop("boolean", "True if the user wants a trailer / edited sequence"));
        Map<String, Object> style = prop("string", "Trailer editing style");
        style.put("enum", enumOf("CINEMATIC", "NERVOUS", "CONTEMPLATIVE"));
        trailer.put("style", style);
        trailer.put("length_seconds", prop("number", "Wanted trailer length, or -1"));

        Map<String, Object> root = Json.obj();
        root.put("reply", prop("string", "One short sentence for the user, in the user's language, saying what you prepared"));
        root.put("shots", shots);
        root.put("trailer", object(trailer));
        return object(root);
    }

    public static ShotRequest fromJson(Map<String, Object> m) {
        ShotRequest r = new ShotRequest();
        r.source = "claude";
        r.reply = Json.getString(m, "reply", "");
        List<Object> shots = Json.getList(m, "shots");
        if (shots != null) {
            for (Map<String, Object> s : Json.objects(shots)) {
                Spec spec = new Spec();
                spec.type = ShotType.byName(Json.getString(s, "type", "AUTO"));
                String moment = Json.getString(s, "moment", "").trim();
                spec.momentId = moment.isEmpty() ? null : moment;
                String kind = Json.getString(s, "kind", "ANY");
                for (Moment.Kind k : Moment.Kind.values()) if (k.name().equalsIgnoreCase(kind)) spec.kind = k;
                String subject = Json.getString(s, "subject", "").trim();
                spec.subject = subject.isEmpty() ? null : subject;
                String place = Json.getString(s, "place", "").trim();
                spec.placeId = place.isEmpty() ? null : place;
                spec.atSec = Json.getDouble(s, "at_seconds", -1);
                spec.durationSec = Json.getDouble(s, "duration_seconds", -1);
                double speed = Json.getDouble(s, "speed", 0);
                spec.speed = speed > 0 ? clamp(speed, 0.05, 20) : Double.NaN;
                spec.distance = clamp(Json.getDouble(s, "distance", 1), 0.2, 6);
                spec.height = clamp(Json.getDouble(s, "height", 1), 0, 8);
                spec.motion = clamp(Json.getDouble(s, "motion", 1), 0.2, 4);
                spec.side = (int) Math.signum(Json.getDouble(s, "side", 0));
                if (spec.durationSec > 0) spec.durationSec = clamp(spec.durationSec, 1, 60);
                r.shots.add(spec);
            }
        }
        Map<String, Object> t = Json.getObj(m, "trailer");
        if (t != null) {
            r.trailer = Json.getBool(t, "enabled", false);
            String style = Json.getString(t, "style", "CINEMATIC");
            for (TrailerBuilder.Style s : TrailerBuilder.Style.values()) if (s.name().equalsIgnoreCase(style)) r.style = s;
            r.trailerSec = Json.getDouble(t, "length_seconds", -1);
        }
        return r;
    }

    static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }
}
