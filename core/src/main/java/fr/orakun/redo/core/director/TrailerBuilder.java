package fr.orakun.redo.core.director;

import fr.orakun.redo.core.timeline.Clip;
import fr.orakun.redo.core.timeline.Sequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Assembles proposed shots into a trailer: opening, build-up, climax, ending. */
public final class TrailerBuilder {
    public enum Style {
        CINEMATIC("Cinématique", 3200, 4800, 45000),
        NERVOUS("Nerveux", 1600, 2800, 35000),
        CONTEMPLATIVE("Contemplatif", 5000, 7000, 60000);

        public final String label;
        public final long minShot;
        public final long maxShot;
        public final long target;

        Style(String label, long minShot, long maxShot, long target) {
            this.label = label;
            this.minShot = minShot;
            this.maxShot = maxShot;
            this.target = target;
        }

        public Style next() {
            Style[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    private TrailerBuilder() {
    }

    public static Sequence build(List<ShotSuggestion> pool, Style style, String name) {
        Sequence seq = new Sequence();
        seq.name = name;
        if (pool.isEmpty()) return seq;
        List<ShotSuggestion> sorted = new ArrayList<ShotSuggestion>(pool);
        Collections.sort(sorted, new Comparator<ShotSuggestion>() {
            @Override
            public int compare(ShotSuggestion a, ShotSuggestion b) {
                return Double.compare(b.score, a.score);
            }
        });

        // opening: the best wide shot on a place
        ShotSuggestion opening = null;
        for (ShotSuggestion s : sorted) {
            ShotType t = s.type();
            if (t != null && t.size == ShotType.Size.WIDE && ("décor".equals(s.momentKind) || "ouverture".equals(s.momentKind))) {
                opening = s;
                break;
            }
        }
        // climax: kills, preferably slow motion
        List<ShotSuggestion> climax = new ArrayList<ShotSuggestion>();
        for (ShotSuggestion s : sorted) {
            if ("kill".equals(s.momentKind) && climax.size() < 2 && !overlapsAny(s, climax)) climax.add(s);
        }
        // body: everything else, best first, until the target length
        long budget = style.target;
        List<ShotSuggestion> body = new ArrayList<ShotSuggestion>();
        long used = (opening != null ? style.maxShot : 0) + climax.size() * style.maxShot;
        for (ShotSuggestion s : sorted) {
            if (s == opening || climax.contains(s)) continue;
            if (style == Style.CONTEMPLATIVE && "combat".equals(s.momentKind) && body.size() > 2) continue;
            if (style == Style.NERVOUS && "décor".equals(s.momentKind) && body.size() > 1) continue;
            if (overlapsAny(s, body) || overlapsAny(s, climax)) continue;
            body.add(s);
            used += (style.minShot + style.maxShot) / 2;
            if (used >= budget) break;
        }
        // chronological story, then avoid two identical shot types in a row
        Collections.sort(body, new Comparator<ShotSuggestion>() {
            @Override
            public int compare(ShotSuggestion a, ShotSuggestion b) {
                return Long.compare(a.clip.sourceStartMs, b.clip.sourceStartMs);
            }
        });
        for (int i = 1; i < body.size(); i++) {
            if (body.get(i).type() == body.get(i - 1).type() && i + 1 < body.size()) {
                Collections.swap(body, i, i + 1);
            }
        }
        Collections.sort(climax, new Comparator<ShotSuggestion>() {
            @Override
            public int compare(ShotSuggestion a, ShotSuggestion b) {
                return Long.compare(a.clip.sourceStartMs, b.clip.sourceStartMs);
            }
        });

        List<ShotSuggestion> order = new ArrayList<ShotSuggestion>();
        if (opening != null) order.add(opening);
        order.addAll(body);
        order.addAll(climax);

        for (int i = 0; i < order.size(); i++) {
            ShotSuggestion s = order.get(i);
            Clip c = s.clip.copy();
            boolean first = i == 0, last = i == order.size() - 1;
            long target = first || last ? style.maxShot : (style.minShot + style.maxShot) / 2;
            if ("kill".equals(s.momentKind)) target = Math.max(target, 3500);
            Director.trimTo(c, Math.min(c.durationMs, target));
            c.transitionIn = first && style != Style.NERVOUS ? Clip.Transition.FADE : Clip.Transition.CUT;
            if (style == Style.CONTEMPLATIVE && !first) c.transitionIn = Clip.Transition.FADE;
            c.transitionMs = style == Style.CONTEMPLATIVE ? 800 : 500;
            seq.append(c);
        }
        return seq;
    }

    private static boolean overlapsAny(ShotSuggestion s, List<ShotSuggestion> list) {
        for (ShotSuggestion o : list) {
            long a0 = s.clip.sourceStartMs, a1 = s.clip.sourceEndMs();
            long b0 = o.clip.sourceStartMs, b1 = o.clip.sourceEndMs();
            if (a0 < b1 - 500 && b0 < a1 - 500) return true;
        }
        return false;
    }
}
