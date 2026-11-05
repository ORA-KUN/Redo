package fr.orakun.redo.core.prompt;

import fr.orakun.redo.core.director.Moment;
import fr.orakun.redo.core.director.ShotType;
import fr.orakun.redo.core.director.TrailerBuilder;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Understands simple French and English requests without any network access, from keywords:
 * "orbite autour de Steve pendant le combat", "drone shot of the base", "bande-annonce nerveuse de 30 s".
 */
public final class OfflinePromptParser {
    private static final Pattern TIME = Pattern.compile("(?:a|at|vers|around|@)\\s*(\\d{1,3})\\s*(?::|min|mn|m)\\s*(\\d{1,2})?");
    private static final Pattern SECONDS = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:s|sec|secs|seconde|secondes|second|seconds)\\b");
    private static final Pattern MINUTES = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:min|mn|minute|minutes)\\b");
    private static final Pattern FACTOR = Pattern.compile("(?:x\\s*(\\d+(?:[.,]\\d+)?)|(\\d+(?:[.,]\\d+)?)\\s*x\\b)");
    private static final Pattern COUNT = Pattern.compile("(\\d+|deux|trois|quatre|cinq|six|two|three|four|five)\\s+(?:plans|shots|plan|prises)");

    private OfflinePromptParser() {
    }

    /** Lower case, no accents, single spaces. */
    static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return " " + n.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("[\\s]+", " ").trim() + " ";
    }

    private static boolean has(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }

    /**
     * @param players names of the players of the replay, used to recognise subjects
     * @param selfName name of the recording player
     */
    public static ShotRequest parse(String prompt, List<String> players, String selfName) {
        ShotRequest r = new ShotRequest();
        r.source = "offline";
        String all = normalize(prompt);

        // trailer
        if (has(all, "trailer", "bande-annonce", "bande annonce", "teaser", "resume de la partie", "best of", "montage complet", "recap")) {
            r.trailer = true;
            if (has(all, "nerv", "dynamique", "rapide", "energique", "action", "fast", "punchy", "intense")) r.style = TrailerBuilder.Style.NERVOUS;
            else if (has(all, "calme", "contemplat", "lent", "doux", "chill", "poetique", "slow", "calm", "relax")) r.style = TrailerBuilder.Style.CONTEMPLATIVE;
            double sec = durationIn(all);
            if (sec > 0) r.trailerSec = Math.max(10, Math.min(300, sec));
        }

        String[] parts = all.split(" puis | ensuite | then | et enfin | and then |;| apres ca | after that ");
        String lastSubject = null;
        for (String part : parts) {
            String p = " " + part.trim() + " ";
            if (p.trim().isEmpty()) continue;
            ShotType type = shotType(p);
            Moment.Kind kind = kind(p);
            String subject = subject(p, players, selfName);
            boolean trailerOnly = r.trailer && type == null && kind == null && subject == null;
            if (trailerOnly) continue;
            if (type == null && kind == null && subject == null && !has(p, "plan", "shot", "camera", "cam ", "filme", "film ")) continue;
            if (subject == null) subject = lastSubject;
            lastSubject = subject;

            ShotRequest.Spec spec = new ShotRequest.Spec();
            spec.type = type;
            spec.kind = kind;
            spec.subject = subject;
            Matcher t = TIME.matcher(p);
            if (t.find()) spec.atSec = Integer.parseInt(t.group(1)) * 60 + (t.group(2) != null ? Integer.parseInt(t.group(2)) : 0);
            if (!r.trailer) {
                double d = durationIn(p);
                if (d > 0) spec.durationSec = Math.max(1, Math.min(60, d));
            }
            if (has(p, "ralenti", "slow-mo", "slowmo", "slow motion", "au ralenti")) spec.speed = 0.3;
            if (has(p, "accelere", "timelapse", "fast forward", "en rapide")) spec.speed = 4;
            Matcher f = FACTOR.matcher(p);
            if (f.find()) {
                String g = f.group(1) != null ? f.group(1) : f.group(2);
                spec.speed = ShotRequest.clamp(Double.parseDouble(g.replace(',', '.')), 0.05, 20);
            }
            if (has(p, "tres proche", "tres pres", "very close", "extreme")) spec.distance = 0.45;
            else if (has(p, " proche", " pres ", " serre", " close ", "tight")) spec.distance = 0.65;
            else if (has(p, "tres loin", "very far")) spec.distance = 2.5;
            else if (has(p, " loin", "eloigne", " far ", "de loin")) spec.distance = 1.7;
            if (has(p, "tres haut", "very high", "du ciel", "from the sky")) spec.height = 3;
            else if (has(p, " en hauteur", " haut ", " high ", " above ")) spec.height = 1.8;
            else if (has(p, " ras du sol", " au sol", "ground level", " bas ", " low ")) spec.height = 0.3;
            if (has(p, "lentement", "doucement", "slowly", "gently", " lent ", " lente ")) spec.motion = 0.55;
            else if (has(p, " vite", "rapidement", "quickly", " fast ", "rapide ")) spec.motion = 1.7;
            if (has(p, "gauche", " left", "anti-horaire", "counter-clockwise", "counterclockwise")) spec.side = -1;
            else if (has(p, "droite", " right", "horaire", "clockwise")) spec.side = 1;

            int count = 1;
            Matcher c = COUNT.matcher(p);
            if (c.find()) count = number(c.group(1));
            else if (has(p, "plusieurs", "quelques", "several", "a few", "some shots")) count = 3;
            count = Math.max(1, Math.min(8, count));
            for (int i = 0; i < count; i++) {
                ShotRequest.Spec s = i == 0 ? spec : copy(spec);
                if (i > 0 && s.type == null) s.side = i % 2 == 0 ? 1 : -1;
                r.shots.add(s);
            }
        }

        StringBuilder reply = new StringBuilder();
        if (!r.shots.isEmpty()) reply.append(r.shots.size()).append(r.shots.size() > 1 ? " plans préparés" : " plan préparé");
        if (r.trailer) {
            if (reply.length() > 0) reply.append(" et ");
            reply.append("une bande-annonce ").append(r.style.label.toLowerCase(Locale.ROOT));
        }
        r.reply = reply.length() == 0 ? "" : reply.append(" (mode hors ligne).").toString();
        return r;
    }

    private static ShotRequest.Spec copy(ShotRequest.Spec s) {
        ShotRequest.Spec c = new ShotRequest.Spec();
        c.type = s.type;
        c.momentId = s.momentId;
        c.kind = s.kind;
        c.subject = s.subject;
        c.atSec = -1;
        c.durationSec = s.durationSec;
        c.speed = s.speed;
        c.distance = s.distance;
        c.height = s.height;
        c.motion = s.motion;
        c.side = s.side;
        return c;
    }

    private static int number(String s) {
        if (s.equals("deux") || s.equals("two")) return 2;
        if (s.equals("trois") || s.equals("three")) return 3;
        if (s.equals("quatre") || s.equals("four")) return 4;
        if (s.equals("cinq") || s.equals("five")) return 5;
        if (s.equals("six")) return 6;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static double durationIn(String p) {
        double total = 0;
        Matcher m = MINUTES.matcher(p);
        if (m.find()) total += Double.parseDouble(m.group(1).replace(',', '.')) * 60;
        Matcher s = SECONDS.matcher(p);
        if (s.find()) total += Double.parseDouble(s.group(1).replace(',', '.'));
        return total;
    }

    static ShotType shotType(String p) {
        if (has(p, "contre-plongee", "contre plongee", "low angle", "low-angle", "par en dessous", "from below")) return ShotType.LOW_ANGLE;
        if (has(p, "plongee", "high angle", "high-angle", "vue de haut", "par au-dessus", "from above", "top down", "top-down")) return ShotType.HIGH_ANGLE;
        if (has(p, "orbit", "tourne autour", "tourner autour", "360", "autour de", "around ", "circle")) return ShotType.ORBIT;
        if (has(p, "drone", "survol", "vue du ciel", "aerien", "aerial", "flyover", "fly over", "vue aerienne")) return ShotType.DRONE;
        if (has(p, "travelling avant", "dolly in", "push in", "zoom avant", "s'approche", "approche de", "zoom in")) return ShotType.DOLLY_IN;
        if (has(p, "travelling arriere", "dolly out", "pull out", "pull back", "zoom arriere", "recule", "s'eloigne", "zoom out", "reveal")) return ShotType.DOLLY_OUT;
        if (has(p, "epaule", "shoulder")) return ShotType.OVER_SHOULDER;
        if (has(p, "gros plan", "close-up", "close up", "closeup", "visage", "face ")) return ShotType.CLOSE_UP;
        if (has(p, "pov", "point de vue", "premiere personne", "first person", "first-person", "ses yeux", "his eyes", "her eyes")) return ShotType.POV;
        if (has(p, "suivi", "suit ", "suivre", "follow", "de dos", "derriere", "behind", "tracking")) return ShotType.FOLLOW;
        if (has(p, "lateral", "truck", "de cote", "from the side", "side shot", "travelling")) return ShotType.TRUCK;
        if (has(p, "grue", "crane", "s'eleve", "monte vers", "rise", "jib")) return ShotType.CRANE;
        if (has(p, "panoramique", " pan ", "panorama", "balayage")) return ShotType.PAN;
        if (has(p, "penche", "dutch", "incline", "tilted")) return ShotType.DUTCH;
        if (has(p, "timelapse", "time-lapse", "time lapse", "accelere")) return ShotType.TIMELAPSE;
        if (has(p, "ralenti", "slow-mo", "slowmo", "slow motion")) return ShotType.SLOW_MOTION;
        if (has(p, "plan large", "plan d'ensemble", "vue d'ensemble", "establishing", "wide shot", "plan d'ouverture")) return ShotType.ESTABLISHING;
        return null;
    }

    static Moment.Kind kind(String p) {
        if (has(p, "kill", " tue ", " tuer", " mort", "meurt", "elimin", "one-shot", "death", " dies", "frag")) return Moment.Kind.KILL;
        if (has(p, "explosion", " tnt", "explose", "boom", "blow up", "creeper")) return Moment.Kind.EXPLOSION;
        if (has(p, "combat", "fight", "pvp", "bagarre", "duel", "baston", "affront", "battle", "clash")) return Moment.Kind.COMBAT;
        if (has(p, "course", "chase", "poursuite", "fuite", "fuit", "sprint", "court ", "running", "run ")) return Moment.Kind.CHASE;
        if (has(p, "construction", "construit", "build", "base", "maison", "house", "batiment")) return Moment.Kind.BUILD;
        if (has(p, "decor", "paysage", "scenery", "landscape", "montagne", "mountain", "foret", "forest", "ocean", "nature")) return Moment.Kind.SCENERY;
        if (has(p, "groupe", "equipe", "team", "tout le monde", "everyone", "group", "ensemble")) return Moment.Kind.GROUP;
        if (has(p, "marqueur", "marker", "repere")) return Moment.Kind.MARKER;
        if (has(p, "debut", "start", "spawn", "arrivee", "ouverture", "intro")) return Moment.Kind.START;
        return null;
    }

    static String subject(String p, List<String> players, String selfName) {
        String best = null;
        for (String name : players) {
            if (name == null || name.length() < 2) continue;
            String n = normalize(name).trim();
            if (p.contains(" " + n + " ") || p.contains(" " + n + "'") || p.contains(" " + n + ",") || p.contains(" " + n + ".")
                    || p.contains("'" + n + " ") || p.contains("@" + n)) {
                if (best == null || name.length() > best.length()) best = name;
            }
        }
        if (best != null) return best;
        if (has(p, " moi ", " me ", " myself", " mon perso", " mon personnage", " my character", " joueur principal")) return "self";
        if (selfName != null && !selfName.isEmpty() && p.contains(" " + normalize(selfName).trim() + " ")) return "self";
        return null;
    }

    /** Lists the shot type names offline users can type, for the help tooltip. */
    public static List<String> examples() {
        List<String> l = new ArrayList<String>();
        l.add("orbite lente autour de moi pendant le combat");
        l.add("survol drone de la base puis plan large d'ouverture");
        l.add("ralenti sur le kill à 12:30");
        l.add("bande-annonce nerveuse de 30 s");
        l.add("trois plans de suivi sur Steve");
        return l;
    }
}
