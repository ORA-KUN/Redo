package fr.orakun.redo.core.prompt;

import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.director.Director;
import fr.orakun.redo.core.director.Landmark;
import fr.orakun.redo.core.director.Moment;
import fr.orakun.redo.core.director.Scene;
import fr.orakun.redo.core.director.ShotParams;
import fr.orakun.redo.core.director.ShotSuggestion;
import fr.orakun.redo.core.director.ShotType;
import fr.orakun.redo.core.director.TrailerBuilder;
import fr.orakun.redo.core.format.RedoMeta;
import fr.orakun.redo.core.math.Vec3d;
import fr.orakun.redo.core.timeline.Sequence;
import fr.orakun.redo.core.track.EntityInfo;
import fr.orakun.redo.core.track.EventTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

/** Turns a text prompt into shots: asks Claude when a key is set, otherwise uses the offline parser. */
public final class PromptService {
    public static final int MAX_MOMENTS_IN_SUMMARY = 30;

    public static final class Result {
        public final List<ShotSuggestion> shots = new ArrayList<ShotSuggestion>();
        public Sequence trailer;
        public String message = "";
        public boolean usedClaude;
        public String warning;
    }

    public interface Callback {
        void done(Result result);
    }

    private static final String SYSTEM =
            "You are the camera operator of Redo, a Minecraft replay mod. The user describes, in French or English, "
                    + "the shots they want from a recorded game session. You receive a summary of the recording: its players and "
                    + "the interesting moments the automatic director detected, each with an id, a kind, a time range and the "
                    + "players involved. Translate the request into shots the director will build.\n"
                    + "Rules:\n"
                    + "- Prefer moment ids from the summary; use at_seconds only when the user gives a time or no moment fits.\n"
                    + "- subject must be a player name copied exactly from the summary, 'self' for the recording player, or empty.\n"
                    + "- Use AUTO as the type when the user does not say how to film; pick types that suit the moment otherwise.\n"
                    + "- Slow motion means speed around 0.3; keep default values (1, 0, -1) for anything the user does not ask.\n"
                    + "- Set trailer.enabled only when the user asks for a trailer, teaser, recap or full edit.\n"
                    + "- When the user names something of the scenery (a tree, a building, a tower, a fountain, water, a portal...), "
                    + "set place to the id of the matching place from the scenery list and leave subject empty. 'au centre du hub', "
                    + "'au milieu' means the matching place nearest to the centre; 'le plus grand' the biggest; 'près de moi' the "
                    + "nearest to the recording player; a compass direction is from the centre. If nothing in the list matches, "
                    + "return no shot for it and say so in reply.\n"
                    + "- Around something (autour, orbit, travelling circulaire) is ORBIT; a travelling along it is TRUCK; towards it "
                    + "DOLLY_IN; flying over it DRONE; rising up it CRANE; looking up at it LOW_ANGLE.\n"
                    + "- Long means a duration of about 14 s (very long 20 s, short 3 s); slow means motion 0.5 (very slow 0.35).\n"
                    + "- Never invent players or moments that are not in the summary. If nothing matches, return no shots and "
                    + "explain why in reply.\n"
                    + "- reply is one short sentence in the user's language.";

    private final Director director;
    private final RedoMeta meta;
    private Scene scene;
    private long nowMs = -1;
    private Vec3d self;

    public PromptService(Director director, RedoMeta meta) {
        this.director = director;
        this.meta = meta;
    }

    /**
     * The scenery around the recording player (read on the main thread before the request), the
     * replay time the user is looking at and where the recording player stands then.
     */
    public PromptService withScene(Scene scene, long nowMs, Vec3d self) {
        this.scene = scene;
        this.nowMs = nowMs;
        this.self = self;
        return this;
    }

    /** Text summary of the recording sent to Claude with the request. */
    public String summary() {
        EventTrack track = director.track();
        StringBuilder sb = new StringBuilder();
        sb.append("Recording: ").append(meta.minecraftVersion).append(", server ").append(meta.serverName)
                .append(", length ").append(Director.formatTime(track.durationMs())).append(".\n");
        sb.append("Recording player (self): ").append(meta.selfName).append(".\n");
        sb.append("Players: ");
        List<String> names = playerNames();
        sb.append(names.isEmpty() ? "none" : join(names)).append(".\n");
        sb.append("Moments (id | kind | start-end | players | description):\n");
        List<Moment> moments = director.moments();
        for (int i = 0; i < moments.size() && i < MAX_MOMENTS_IN_SUMMARY; i++) {
            Moment m = moments.get(i);
            sb.append('m').append(i + 1).append(" | ").append(m.kind.name()).append(" | ")
                    .append(Director.formatTime(m.startMs)).append('-').append(Director.formatTime(m.endMs)).append(" | ");
            List<String> who = new ArrayList<String>();
            for (int id : m.subjects) {
                EntityInfo e = track.entity(id);
                if (e != null) who.add(e.self ? meta.selfName + " (self)" : e.displayName());
            }
            sb.append(who.isEmpty() ? "-" : join(who)).append(" | ").append(m.label).append('\n');
        }
        if (nowMs >= 0) sb.append("The user is looking at replay time ").append(Director.formatTime(nowMs)).append(".\n");
        if (scene != null) sb.append(scene.summary());
        sb.append("Shot types: ");
        for (ShotType t : ShotType.values()) sb.append(t.name()).append(" (").append(t.label).append("), ");
        sb.setLength(sb.length() - 2);
        sb.append(".\n");
        return sb.toString();
    }

    public List<String> playerNames() {
        List<String> names = new ArrayList<String>();
        for (EntityInfo e : director.track().players()) {
            String n = e.self ? meta.selfName : e.name;
            if (n != null && !n.isEmpty() && !names.contains(n)) names.add(n);
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Runs the prompt on a background thread and calls back on {@code mainThread}.
     * Falls back to the offline parser when there is no key or the API fails.
     */
    public void run(final String prompt, final ClaudeClient client, final long seed, final Executor mainThread, final Callback callback) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                ShotRequest request = null;
                String warning = null;
                if (client != null && client.hasKey()) {
                    try {
                        String user = summary() + "\nUser request: " + prompt;
                        request = ShotRequest.fromJson(client.structured(SYSTEM, user, ShotRequest.schema(), 4000));
                    } catch (Exception e) {
                        RedoLog.warn("Claude prompt failed: " + e);
                        warning = e.getMessage() + " Mode hors ligne utilisé.";
                    }
                }
                if (request == null) request = OfflinePromptParser.parse(prompt, playerNames(), meta.selfName);
                final ShotRequest req = request;
                final String warn = warning;
                mainThread.execute(new Runnable() {
                    @Override
                    public void run() {
                        Result r = execute(req, seed);
                        r.warning = warn;
                        callback.done(r);
                    }
                });
            }
        }, "Redo prompt");
        t.setDaemon(true);
        t.start();
    }

    /** Builds the shots of a request. Must run where the world can be queried (main thread). */
    public Result execute(ShotRequest req, long seed) {
        Result r = new Result();
        r.usedClaude = "claude".equals(req.source);
        EventTrack track = director.track();
        List<Moment> moments = director.moments();
        int i = 0;
        List<String> missing = new ArrayList<String>();
        for (ShotRequest.Spec spec : req.shots) {
            Landmark place = null;
            if (spec.placeId != null || spec.place != null) {
                place = resolvePlace(spec);
                if (place == null) {
                    String what = spec.place != null && !spec.place.noun.isEmpty() ? spec.place.noun : "cet élément du décor";
                    if (!missing.contains(what)) missing.add(what);
                    continue;
                }
            }
            if (place != null) {
                ShotSuggestion s = placeShot(spec, place, seed + 7919L * (i++));
                if (s != null) r.shots.add(s);
                continue;
            }
            int subject = resolveSubject(spec.subject, track);
            Moment m = null;
            if (spec.momentId != null && spec.momentId.matches("[mM]\\d+")) {
                int idx = Integer.parseInt(spec.momentId.substring(1)) - 1;
                if (idx >= 0 && idx < moments.size()) m = moments.get(idx);
            }
            if (m == null && spec.atSec >= 0) m = director.momentAt((long) (spec.atSec * 1000), subject);
            if (m == null && spec.kind != null) {
                m = director.bestMoment(spec.kind, subject);
                if (m == null) m = director.bestMoment(spec.kind, Integer.MIN_VALUE);
            }
            if (m == null) m = director.bestMoment(null, subject);
            if (m == null) m = director.bestMoment(null, Integer.MIN_VALUE);
            if (m == null) m = director.momentAt(track.durationMs() / 2, subject);
            ShotType type = spec.type != null ? spec.type : Director.fitting(m.kind)[0];
            ShotParams p = new ShotParams();
            if (spec.durationSec > 0) p.durationMs = (long) (spec.durationSec * 1000);
            if (!Double.isNaN(spec.speed)) p.speed = spec.speed;
            if (spec.atSec >= 0) p.sourceStartMs = (long) (spec.atSec * 1000);
            if (subject != Integer.MIN_VALUE) p.subject = subject;
            p.distanceScale = spec.distance;
            p.heightScale = spec.height;
            p.motion = spec.motion;
            p.side = spec.side;
            ShotSuggestion s = director.generate(type, m, p, seed + 7919L * (i++));
            if (s == null && type.needsSubject()) s = director.generate(ShotType.ESTABLISHING, m, p, seed + 31L * i);
            if (s == null) continue;
            s.description = type.label + " · " + m.label;
            r.shots.add(s);
        }
        if (req.trailer) {
            List<ShotSuggestion> pool = director.analyse(seed);
            pool.addAll(r.shots);
            TrailerBuilder.Style style = req.style;
            r.trailer = TrailerBuilder.build(pool, style, "Bande-annonce " + style.label.toLowerCase(Locale.ROOT));
            if (req.trailerSec > 0) fitLength(r.trailer, (long) (req.trailerSec * 1000));
        }
        boolean placed = false;
        for (ShotSuggestion s : r.shots) placed |= s.place != null;
        r.message = req.reply != null && !req.reply.isEmpty() && !(placed && "offline".equals(req.source)) ? req.reply : describe(r);
        if (!missing.isEmpty()) {
            String m = "Je n'ai pas trouvé " + (missing.size() == 1 ? article(missing.get(0)) : "ces éléments") + " dans le décor autour de toi"
                    + (scene == null ? " (le monde n'est pas chargé ici)." : ".");
            // nothing made: the reply would announce shots that are not there
            r.message = r.shots.isEmpty() && r.trailer == null ? m : r.message.isEmpty() ? m : r.message + " " + m;
        }
        if (r.shots.isEmpty() && r.trailer == null && r.message.isEmpty()) {
            r.message = "Je n'ai pas compris la demande. Exemple : « " + OfflinePromptParser.examples().get(0) + " ».";
        }
        return r;
    }

    /** The place a shot asks for, found in the scenery. */
    private Landmark resolvePlace(ShotRequest.Spec spec) {
        if (scene == null) return null;
        if (spec.placeId != null) return scene.byId(spec.placeId);
        PlaceQuery q = spec.place;
        if (q.wholePlace) {
            // the place itself: its centre, as wide as what stands around it
            double reach = 10;
            for (Landmark l : scene.landmarks) if (l.distanceToCentre < 40) reach = Math.max(reach, l.distanceToCentre * 0.6);
            Landmark c = new Landmark(Landmark.Kind.BUILDING, "Centre du " + q.noun, scene.centre, Math.min(reach, 24), 6, 0);
            c.id = "centre";
            return c;
        }
        if (q.labelHint != null) {
            Landmark best = null;
            for (Landmark l : scene.landmarks) {
                if (!l.label.startsWith(q.labelHint)) continue;
                if (best == null || (q.pick == Scene.Pick.CENTRAL ? l.distanceToCentre < best.distanceToCentre : l.size > best.size)) best = l;
            }
            return best;
        }
        Scene.Pick pick = q.pick;
        // "l'arbre" alone in a hub: the one in the middle is the one people mean
        if (pick == Scene.Pick.ANY && scene.centreFromBuildings) pick = Scene.Pick.CENTRAL;
        if (pick == Scene.Pick.ANY) pick = Scene.Pick.BIGGEST;
        Landmark found = scene.find(q.kinds, pick, q.direction, self);
        // a big statue reads as a building: still the thing asked for
        if (found == null && q.kinds.contains(Landmark.Kind.MONUMENT)) found = scene.find(Scene.kinds(Landmark.Kind.BUILDING), pick, q.direction, self);
        return found;
    }

    private ShotSuggestion placeShot(ShotRequest.Spec spec, Landmark place, long seed) {
        EventTrack track = director.track();
        long at = spec.atSec >= 0 ? (long) (spec.atSec * 1000) : nowMs >= 0 ? nowMs : track.startMs();
        Moment m = director.momentAt(at, Integer.MIN_VALUE);
        m.label = place.label;
        ShotType type = spec.type != null ? spec.type : ShotType.ORBIT;
        ShotParams p = new ShotParams();
        p.place = place;
        p.sourceStartMs = Math.max(track.startMs(), at);
        if (spec.durationSec > 0) p.durationMs = (long) (spec.durationSec * 1000);
        if (!Double.isNaN(spec.speed)) p.speed = spec.speed;
        p.distanceScale = spec.distance;
        p.heightScale = spec.height;
        p.motion = spec.motion;
        p.side = spec.side;
        ShotSuggestion s = director.generate(type, m, p, seed);
        if (s == null) return null;
        s.place = place;
        s.description = type.label + " · " + place.label;
        return s;
    }

    private static String article(String noun) {
        if (noun.isEmpty()) return "cet élément";
        char c = noun.charAt(0);
        boolean vowel = "aeiouyéèêâàôh".indexOf(Character.toLowerCase(c)) >= 0;
        boolean feminine = noun.equals("tour") || noun.equals("fontaine") || noun.equals("eau") || noun.equals("lave")
                || noun.equals("balise") || noun.equals("cloche") || noun.equals("enclume") || noun.equals("statue");
        return vowel ? "l'" + noun : (feminine ? "la " : "le ") + noun;
    }

    private static String describe(Result r) {
        if (r.shots.isEmpty() && r.trailer == null) return "";
        if (r.shots.size() == 1 && r.trailer == null && r.shots.get(0).place != null) {
            ShotSuggestion s = r.shots.get(0);
            Landmark l = s.place;
            return s.description + (l.distanceToCentre < 4 ? " (au centre)" : "") + ", " + Director.formatTime(s.clip.durationMs)
                    + ", ajouté au chutier.";
        }
        StringBuilder sb = new StringBuilder();
        if (!r.shots.isEmpty()) sb.append(r.shots.size()).append(r.shots.size() > 1 ? " plans ajoutés au chutier" : " plan ajouté au chutier");
        if (r.trailer != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("bande-annonce de ").append(Director.formatTime(r.trailer.duration())).append(" créée");
        }
        return sb.append('.').toString();
    }

    private static int resolveSubject(String name, EventTrack track) {
        if (name == null || name.isEmpty()) return Integer.MIN_VALUE;
        if (name.equalsIgnoreCase("self")) return track.selfId();
        EntityInfo e = track.playerByName(name);
        if (e == null) return Integer.MIN_VALUE;
        return e.self ? track.selfId() : e.id;
    }

    /** Scales clip lengths so the sequence lasts about {@code targetMs}. */
    static void fitLength(Sequence seq, long targetMs) {
        long total = seq.duration();
        if (total <= 0 || seq.clips.isEmpty()) return;
        double ratio = targetMs / (double) total;
        if (ratio < 1) {
            // drop the weakest middle clips first when far too long, then shorten
            while (seq.clips.size() > 3 && seq.duration() * 0.6 > targetMs) {
                seq.clips.remove(seq.clips.size() / 2);
                seq.closeGaps();
            }
            ratio = targetMs / (double) seq.duration();
            for (fr.orakun.redo.core.timeline.Clip c : seq.clips) {
                Director.trimTo(c, Math.max(1200, (long) (c.durationMs * ratio)));
            }
            seq.closeGaps();
        }
    }
}
