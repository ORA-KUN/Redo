package fr.orakun.redo.core.track;

import fr.orakun.redo.core.math.Angles;
import fr.orakun.redo.core.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything Redo knows about a recording in a version-independent form. */
public final class EventTrack {
    private final Map<Integer, EntityInfo> entities = new LinkedHashMap<Integer, EntityInfo>();
    private final Map<Integer, List<EntitySample>> samples = new HashMap<Integer, List<EntitySample>>();
    private final Map<Integer, Integer> removals = new HashMap<Integer, Integer>();
    private final List<GameEvent> events = new ArrayList<GameEvent>();
    private final List<TerrainSample> terrain = new ArrayList<TerrainSample>();
    private final List<SelfSample> self = new ArrayList<SelfSample>();
    private final List<SelfEquipment> equipment = new ArrayList<SelfEquipment>();
    private int selfId = Integer.MIN_VALUE;
    private int durationMs;

    public void addEntity(EntityInfo e) {
        entities.put(e.id, e);
        if (e.self) selfId = e.id;
    }

    public void addSample(int id, EntitySample s) {
        List<EntitySample> l = samples.get(id);
        if (l == null) {
            l = new ArrayList<EntitySample>();
            samples.put(id, l);
        }
        l.add(s);
        durationMs = Math.max(durationMs, s.timeMs);
    }

    public void addEvent(GameEvent e) {
        events.add(e);
        durationMs = Math.max(durationMs, e.timeMs);
    }

    public void addTerrain(TerrainSample t) {
        terrain.add(t);
    }

    public void addSelf(SelfSample s) {
        self.add(s);
        durationMs = Math.max(durationMs, s.timeMs);
    }

    public void addEquipment(SelfEquipment e) {
        equipment.add(e);
    }

    public void addRemoval(int id, int timeMs) {
        removals.put(id, timeMs);
    }

    /** Sorts everything and mirrors the self samples as entity samples of the self entity. */
    public void finish() {
        for (List<EntitySample> l : samples.values()) {
            Collections.sort(l, new Comparator<EntitySample>() {
                @Override
                public int compare(EntitySample a, EntitySample b) {
                    return Integer.compare(a.timeMs, b.timeMs);
                }
            });
        }
        Collections.sort(events, new Comparator<GameEvent>() {
            @Override
            public int compare(GameEvent a, GameEvent b) {
                return Integer.compare(a.timeMs, b.timeMs);
            }
        });
        if (selfId != Integer.MIN_VALUE && !self.isEmpty() && !samples.containsKey(selfId)) {
            List<EntitySample> l = new ArrayList<EntitySample>();
            for (int i = 0; i < self.size(); i += 2) { // 10 Hz is plenty for the director
                SelfSample s = self.get(i);
                l.add(new EntitySample(s.timeMs, (float) s.x, (float) s.y, (float) s.z, s.yaw, s.pitch, s.flags));
            }
            samples.put(selfId, l);
        }
    }

    public void setDuration(int ms) {
        durationMs = Math.max(durationMs, ms);
    }

    public int durationMs() {
        return durationMs;
    }

    public int selfId() {
        return selfId;
    }

    public EntityInfo entity(int id) {
        return entities.get(id);
    }

    public Collection<EntityInfo> entities() {
        return entities.values();
    }

    public List<EntityInfo> players() {
        List<EntityInfo> l = new ArrayList<EntityInfo>();
        for (EntityInfo e : entities.values()) if (e.player) l.add(e);
        return l;
    }

    public EntityInfo playerByName(String name) {
        for (EntityInfo e : entities.values()) {
            if (e.player && e.name.equalsIgnoreCase(name)) return e;
        }
        for (EntityInfo e : entities.values()) {
            if (e.player && e.name.toLowerCase().contains(name.toLowerCase())) return e;
        }
        return null;
    }

    public List<EntitySample> samples(int id) {
        List<EntitySample> l = samples.get(id);
        return l == null ? Collections.<EntitySample>emptyList() : l;
    }

    public List<GameEvent> events() {
        return events;
    }

    public List<TerrainSample> terrain() {
        return terrain;
    }

    public List<SelfSample> self() {
        return self;
    }

    public List<SelfEquipment> equipment() {
        return equipment;
    }

    /** The recording player's state at time t, interpolated between ticks, or null. */
    public SelfSample selfAt(double t) {
        if (self.isEmpty()) return null;
        int lo = 0, hi = self.size() - 1, best = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (self.get(mid).timeMs <= t) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (best < 0) return self.get(0);
        SelfSample a = self.get(best);
        if (best == self.size() - 1) return a;
        SelfSample b = self.get(best + 1);
        if (b.timeMs - a.timeMs > 1000) return a;
        double u = (t - a.timeMs) / Math.max(1.0, b.timeMs - a.timeMs);
        return new SelfSample((int) t, a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u, a.z + (b.z - a.z) * u,
                (float) Angles.lerp(a.yaw, Angles.unwrapNear(a.yaw, b.yaw), u),
                (float) (a.pitch + (b.pitch - a.pitch) * u),
                (float) Angles.lerp(a.headYaw, Angles.unwrapNear(a.headYaw, b.headYaw), u),
                (float) Angles.lerp(a.bodyYaw, Angles.unwrapNear(a.bodyYaw, b.bodyYaw), u),
                u < 0.5 ? a.flags : b.flags, a.actions | b.actions);
    }

    public Integer removalTime(int id) {
        return removals.get(id);
    }

    /** Index of the last sample at or before t (binary search), or -1. */
    private static int floor(List<EntitySample> l, double t) {
        int lo = 0, hi = l.size() - 1, best = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (l.get(mid).timeMs <= t) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return best;
    }

    /** Interpolated position, or null if the entity was not tracked around that time. */
    public Vec3d positionAt(int id, double t) {
        List<EntitySample> l = samples.get(id);
        if (l == null || l.isEmpty()) return null;
        int i = floor(l, t);
        if (i < 0) {
            EntitySample f = l.get(0);
            return f.timeMs - t < 1500 ? f.pos() : null;
        }
        EntitySample a = l.get(i);
        if (i == l.size() - 1) return t - a.timeMs < 1500 ? a.pos() : null;
        EntitySample b = l.get(i + 1);
        if (b.timeMs - a.timeMs > 5000) return t - a.timeMs < 1500 ? a.pos() : null;
        double u = (t - a.timeMs) / Math.max(1.0, b.timeMs - a.timeMs);
        return a.pos().lerp(b.pos(), u);
    }

    public EntitySample sampleAt(int id, double t) {
        List<EntitySample> l = samples.get(id);
        if (l == null || l.isEmpty()) return null;
        int i = floor(l, t);
        return l.get(Math.max(0, i));
    }

    /** Interpolated yaw (degrees). */
    public double yawAt(int id, double t) {
        List<EntitySample> l = samples.get(id);
        if (l == null || l.isEmpty()) return 0;
        int i = floor(l, t);
        if (i < 0) return l.get(0).yaw;
        if (i == l.size() - 1) return l.get(i).yaw;
        EntitySample a = l.get(i), b = l.get(i + 1);
        double u = (t - a.timeMs) / Math.max(1.0, b.timeMs - a.timeMs);
        return Angles.wrap(Angles.lerp(a.yaw, Angles.unwrapNear(a.yaw, b.yaw), u));
    }

    /** Horizontal speed in blocks per second around t. */
    public double speedAt(int id, double t) {
        Vec3d a = positionAt(id, t - 250);
        Vec3d b = positionAt(id, t + 250);
        if (a == null || b == null) return 0;
        return b.sub(a).horizontalLength() * 2.0;
    }

    /** Smoothed position (average over a window), useful for camera targets. */
    public Vec3d smoothPositionAt(int id, double t, double windowMs) {
        Vec3d sum = Vec3d.ZERO;
        int n = 0;
        for (int k = -2; k <= 2; k++) {
            Vec3d p = positionAt(id, t + k * windowMs / 4.0);
            if (p != null) {
                sum = sum.add(p);
                n++;
            }
        }
        return n == 0 ? null : sum.mul(1.0 / n);
    }

    /** Terrain sample closest in time that covers (x, z), or null. */
    public TerrainSample terrainAt(double x, double z, double t) {
        TerrainSample best = null;
        double bestDt = Double.MAX_VALUE;
        for (TerrainSample s : terrain) {
            if (!s.contains(x, z)) continue;
            double dt = Math.abs(s.timeMs - t);
            if (dt < bestDt) {
                bestDt = dt;
                best = s;
            }
        }
        return best;
    }

    /** Ground height from terrain samples, or NaN if unknown. */
    public double groundAt(double x, double z, double t) {
        TerrainSample s = terrainAt(x, z, t);
        return s == null ? Double.NaN : s.heightAt(x, z);
    }

    public List<GameEvent> events(GameEvent.Type type) {
        List<GameEvent> l = new ArrayList<GameEvent>();
        for (GameEvent e : events) if (e.type == type) l.add(e);
        return l;
    }

    /** Ids of entities with samples within {@code radius} of {@code p} at time t. */
    public List<Integer> entitiesNear(Vec3d p, double t, double radius, boolean playersOnly) {
        List<Integer> out = new ArrayList<Integer>();
        double r2 = radius * radius;
        for (Map.Entry<Integer, List<EntitySample>> e : samples.entrySet()) {
            EntityInfo info = entities.get(e.getKey());
            if (playersOnly && (info == null || !info.player)) continue;
            Vec3d q = positionAt(e.getKey(), t);
            if (q != null && q.distanceSq(p) <= r2) out.add(e.getKey());
        }
        return out;
    }
}
