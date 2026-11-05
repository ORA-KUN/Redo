package fr.orakun.redo.core.director;

import fr.orakun.redo.core.format.RedoMeta;
import fr.orakun.redo.core.math.Vec3d;
import fr.orakun.redo.core.timeline.Marker;
import fr.orakun.redo.core.track.EntityInfo;
import fr.orakun.redo.core.track.EntitySample;
import fr.orakun.redo.core.track.EventTrack;
import fr.orakun.redo.core.track.GameEvent;
import fr.orakun.redo.core.track.TerrainSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Finds the moments worth filming: fights, kills, runs, builds, scenery, groups, markers. */
public final class MomentDetector {
    private final EventTrack track;
    private final RedoMeta meta;

    public MomentDetector(EventTrack track, RedoMeta meta) {
        this.track = track;
        this.meta = meta;
    }

    public List<Moment> detect() {
        List<Moment> all = new ArrayList<Moment>();
        all.addAll(combats());
        all.addAll(kills());
        all.addAll(chases());
        all.addAll(builds());
        all.addAll(scenery());
        all.addAll(groups());
        all.addAll(markers());
        all.addAll(explosions());
        Moment start = start();
        if (start != null) all.add(start);
        applyHighlights(all);
        Collections.sort(all, new Comparator<Moment>() {
            @Override
            public int compare(Moment a, Moment b) {
                return Double.compare(b.score, a.score);
            }
        });
        return dedupe(all);
    }

    private boolean isPlayer(int id) {
        EntityInfo e = track.entity(id);
        return e != null && e.player;
    }

    private String name(int id) {
        EntityInfo e = track.entity(id);
        return e == null ? "?" : e.displayName();
    }

    // ---------------------------------------------------------------- combats

    private List<Moment> combats() {
        List<Moment> out = new ArrayList<Moment>();
        List<GameEvent> hits = new ArrayList<GameEvent>();
        for (GameEvent e : track.events(GameEvent.Type.DAMAGE)) {
            boolean pvp = isPlayer(e.entity) && (e.other == GameEvent.NONE || isPlayer(e.other));
            boolean selfFight = e.entity == track.selfId() || e.other == track.selfId();
            if (pvp || selfFight) hits.add(e);
        }
        int i = 0;
        while (i < hits.size()) {
            GameEvent first = hits.get(i);
            Set<Integer> who = new LinkedHashSet<Integer>();
            Vec3d center = first.pos();
            int last = first.timeMs;
            int count = 0;
            int j = i;
            while (j < hits.size()) {
                GameEvent h = hits.get(j);
                if (h.timeMs - last > 5000) break;
                if (count > 0 && h.pos().distance(center) > 24) break;
                who.add(h.entity);
                if (h.other != GameEvent.NONE) who.add(h.other);
                center = center.mul(count / (count + 1.0)).add(h.pos().mul(1.0 / (count + 1)));
                last = h.timeMs;
                count++;
                j++;
            }
            if (count >= 2) {
                boolean withSelf = who.contains(track.selfId());
                double score = Math.min(1.0, 0.35 + count * 0.06 + (who.size() > 2 ? 0.1 : 0) + (withSelf ? 0.1 : 0));
                Moment m = new Moment(Moment.Kind.COMBAT, Math.max(0, first.timeMs - 1500), last + 1500,
                        first.timeMs + (last - first.timeMs) / 2, center, score, "");
                // self first, then others
                if (withSelf) m.subjects.add(track.selfId());
                for (Integer w : who) if (!m.subjects.contains(w)) m.subjects.add(w);
                m.label = describeFight(m);
                out.add(m);
            }
            i = Math.max(j, i + 1);
        }
        return out;
    }

    private String describeFight(Moment m) {
        if (m.subjects.size() >= 2) return "Combat " + name(m.subjects.get(0)) + " contre " + name(m.subjects.get(1));
        return "Combat de " + name(m.mainSubject());
    }

    // ---------------------------------------------------------------- kills

    private List<Moment> kills() {
        List<Moment> out = new ArrayList<Moment>();
        for (GameEvent d : track.events(GameEvent.Type.DEATH)) {
            boolean player = isPlayer(d.entity);
            boolean selfInvolved = d.entity == track.selfId() || d.other == track.selfId();
            if (!player && !selfInvolved) continue;
            double score = player ? 0.85 : 0.5;
            if (selfInvolved) score += 0.1;
            String label;
            if (d.other != GameEvent.NONE) label = name(d.other) + " élimine " + name(d.entity);
            else label = name(d.entity) + " meurt";
            Moment m = new Moment(Moment.Kind.KILL, Math.max(0, d.timeMs - 3000), d.timeMs + 1500, d.timeMs,
                    d.pos(), Math.min(1, score), label);
            if (d.other != GameEvent.NONE) m.subjects.add(d.other);
            m.subjects.add(d.entity);
            out.add(m);
        }
        return out;
    }

    // ---------------------------------------------------------------- runs

    private List<Moment> chases() {
        List<Moment> out = new ArrayList<Moment>();
        for (EntityInfo e : track.players()) {
            List<EntitySample> l = track.samples(e.id);
            if (l.size() < 10) continue;
            int segStart = -1;
            double maxSpeed = 0;
            boolean elytra = false;
            for (int i = 1; i < l.size(); i++) {
                EntitySample a = l.get(i - 1), b = l.get(i);
                double dt = (b.timeMs - a.timeMs) / 1000.0;
                if (dt <= 0 || dt > 2) {
                    segStart = -1;
                    continue;
                }
                double v = Math.hypot(b.x - a.x, b.z - a.z) / dt;
                boolean fast = v > 5.2 || b.has(EntitySample.ELYTRA);
                if (fast) {
                    if (segStart < 0) {
                        segStart = a.timeMs;
                        maxSpeed = 0;
                        elytra = false;
                    }
                    maxSpeed = Math.max(maxSpeed, v);
                    elytra |= b.has(EntitySample.ELYTRA);
                }
                if ((!fast || i == l.size() - 1) && segStart >= 0) {
                    int end = a.timeMs;
                    if (end - segStart >= 3000) {
                        double score = Math.min(0.8, 0.25 + (end - segStart) / 40000.0 + maxSpeed / 60.0 + (elytra ? 0.15 : 0));
                        if (e.self) score += 0.05;
                        Vec3d mid = track.positionAt(e.id, (segStart + end) / 2.0);
                        Moment m = new Moment(Moment.Kind.CHASE, segStart, Math.min(end, segStart + 12000),
                                (segStart + end) / 2, mid, score, (elytra ? "Vol en elytra de " : "Course de ") + e.displayName());
                        m.subjects.add(e.id);
                        out.add(m);
                    }
                    segStart = -1;
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- builds

    private List<Moment> builds() {
        List<Moment> out = new ArrayList<Moment>();
        List<GameEvent> places = track.events(GameEvent.Type.BLOCK_PLACE);
        int i = 0;
        while (i < places.size()) {
            GameEvent f = places.get(i);
            int j = i;
            Vec3d c = f.pos();
            int n = 0;
            while (j < places.size() && places.get(j).timeMs - f.timeMs < 60000 && places.get(j).pos().distance(c) < 16) {
                n++;
                j++;
            }
            if (n >= 15) {
                GameEvent last = places.get(j - 1);
                Moment m = new Moment(Moment.Kind.BUILD, f.timeMs, last.timeMs + 2000, (f.timeMs + last.timeMs) / 2,
                        c, Math.min(0.7, 0.3 + n / 150.0), "Construction (" + n + " blocs)");
                if (f.entity != GameEvent.NONE) m.subjects.add(f.entity);
                out.add(m);
                i = j;
            } else {
                i++;
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- scenery

    private List<Moment> scenery() {
        List<Moment> out = new ArrayList<Moment>();
        List<TerrainSample> ts = track.terrain();
        for (TerrainSample t : ts) {
            double relief = t.relief();
            double water = t.waterRatio();
            Vec3d selfPos = track.selfId() != Integer.MIN_VALUE ? track.positionAt(track.selfId(), t.timeMs) : null;
            double altitude = selfPos == null ? 0 : selfPos.y - t.minHeight();
            double score = Math.min(0.75, relief / 25.0 + (water > 0.2 && water < 0.8 ? 0.15 : 0) + Math.max(0, altitude - 15) / 80.0
                    + (t.count("lava") > 0 ? 0.1 : 0) + (t.count("snow") > 3 ? 0.05 : 0));
            if (score < 0.3) continue;
            // focus on the highest point of the grid, a natural landmark
            int best = 0;
            for (int k = 1; k < t.heights.length; k++) if (t.heights[k] > t.heights[best]) best = k;
            int ix = best % t.size, iz = best / t.size;
            Vec3d peak = new Vec3d(t.originX + ix * t.step, t.heights[best], t.originZ + iz * t.step);
            Vec3d focus = selfPos != null ? selfPos.lerp(peak, 0.5) : peak;
            String what = relief > 12 ? "Relief" : (water > 0.2 ? "Bord de l'eau" : "Panorama");
            Moment m = new Moment(Moment.Kind.SCENERY, Math.max(0, t.timeMs - 3000), t.timeMs + 5000, t.timeMs,
                    focus, score, what);
            if (selfPos != null) m.subjects.add(track.selfId());
            out.add(m);
        }
        // keep the best scenery every 45 seconds
        List<Moment> kept = new ArrayList<Moment>();
        Collections.sort(out, new Comparator<Moment>() {
            @Override
            public int compare(Moment a, Moment b) {
                return Double.compare(b.score, a.score);
            }
        });
        for (Moment m : out) {
            boolean close = false;
            for (Moment k : kept) if (Math.abs(k.peakMs - m.peakMs) < 45000) close = true;
            if (!close) kept.add(m);
        }
        return kept;
    }

    // ---------------------------------------------------------------- groups

    private List<Moment> groups() {
        List<Moment> out = new ArrayList<Moment>();
        List<EntityInfo> players = track.players();
        if (players.size() < 3) return out;
        int lastGroup = -100000;
        for (int t = 0; t < track.durationMs(); t += 5000) {
            for (EntityInfo p : players) {
                Vec3d c = track.positionAt(p.id, t);
                if (c == null) continue;
                List<Integer> near = track.entitiesNear(c, t, 10, true);
                if (near.size() >= 3 && t - lastGroup > 30000) {
                    Moment m = new Moment(Moment.Kind.GROUP, Math.max(0, t - 2000), t + 4000, t, c,
                            Math.min(0.6, 0.3 + near.size() * 0.05), "Groupe de " + near.size() + " joueurs");
                    m.subjects.addAll(near);
                    out.add(m);
                    lastGroup = t;
                    break;
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- markers

    private List<Moment> markers() {
        List<Moment> out = new ArrayList<Moment>();
        for (Marker mk : meta.markers) {
            int t = (int) mk.timeMs;
            Vec3d p = track.selfId() != Integer.MIN_VALUE ? track.positionAt(track.selfId(), t) : null;
            Moment m = new Moment(Moment.Kind.MARKER, Math.max(0, t - 4000), t + 3000, t, p, 0.8,
                    mk.label == null || mk.label.isEmpty() ? "Marqueur" : mk.label);
            if (p != null) m.subjects.add(track.selfId());
            out.add(m);
        }
        return out;
    }

    private List<Moment> explosions() {
        List<Moment> out = new ArrayList<Moment>();
        int last = -100000;
        for (GameEvent e : track.events(GameEvent.Type.EXPLOSION)) {
            if (e.timeMs - last < 8000) continue;
            last = e.timeMs;
            out.add(new Moment(Moment.Kind.EXPLOSION, Math.max(0, e.timeMs - 2000), e.timeMs + 2000, e.timeMs,
                    e.pos(), 0.55, "Explosion"));
        }
        return out;
    }

    private Moment start() {
        if (track.selfId() == Integer.MIN_VALUE) return null;
        int t = Math.min(8000, track.durationMs() / 10);
        Vec3d p = track.positionAt(track.selfId(), t);
        if (p == null) return null;
        Moment m = new Moment(Moment.Kind.START, Math.max(0, t - 2000), t + 6000, t, p, 0.25, "Ouverture");
        m.subjects.add(track.selfId());
        return m;
    }

    // ---------------------------------------------------------------- post

    private void applyHighlights(List<Moment> all) {
        if (meta.highlights.isEmpty()) return;
        for (Moment m : all) {
            boolean inside = false;
            for (long[] h : meta.highlights) if (m.peakMs >= h[0] && m.peakMs <= h[1]) inside = true;
            m.score *= inside ? 1.3 : 0.55;
            m.score = Math.min(1, m.score);
        }
    }

    /** Removes weaker moments of the same kind that overlap a stronger one. */
    private static List<Moment> dedupe(List<Moment> sorted) {
        List<Moment> out = new ArrayList<Moment>();
        for (Moment m : sorted) {
            boolean dup = false;
            for (Moment o : out) {
                if (o.kind == m.kind && o.overlaps(m)) {
                    dup = true;
                    break;
                }
                // a kill already covers the end of its fight
                if (o.kind == Moment.Kind.KILL && m.kind == Moment.Kind.COMBAT && Math.abs(o.peakMs - m.endMs) < 3000
                        && m.duration() < 6000) {
                    dup = true;
                    break;
                }
            }
            if (!dup) out.add(m);
        }
        return out;
    }
}
