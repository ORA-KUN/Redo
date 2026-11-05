package fr.orakun.redo.core.timeline;

import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.json.Json;
import fr.orakun.redo.core.json.JsonSerializable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** An edit ("montage"): clips on the camera track plus timeline markers and output settings. */
public final class Sequence implements JsonSerializable {
    public String name = "Montage 1";
    public final List<Clip> clips = new ArrayList<Clip>();
    public final List<Marker> markers = new ArrayList<Marker>();
    public ExportSettings export = new ExportSettings();
    /** Hide the HUD, nametags of the recorded player etc. while rendering. */
    public boolean hideHud = true;
    public boolean hideNametags = false;

    public static final Comparator<Clip> BY_START = new Comparator<Clip>() {
        @Override
        public int compare(Clip a, Clip b) {
            return Long.compare(a.timelineStartMs, b.timelineStartMs);
        }
    };

    public long duration() {
        long d = 0;
        for (Clip c : clips) d = Math.max(d, c.timelineEndMs());
        return d;
    }

    public void sort() {
        Collections.sort(clips, BY_START);
    }

    public Clip clipAt(double t) {
        for (Clip c : clips) if (c.containsTimeline(t)) return c;
        return null;
    }

    public Clip byId(String id) {
        for (Clip c : clips) if (c.id.equals(id)) return c;
        return null;
    }

    /** Result of evaluating the sequence at a given output time. */
    public static final class Frame {
        public final Clip clip;
        public final double replayTimeMs;
        public final CameraState camera;
        /** 0 = fully visible, 1 = black (fade). */
        public final double fade;

        Frame(Clip clip, double replayTimeMs, CameraState camera, double fade) {
            this.clip = clip;
            this.replayTimeMs = replayTimeMs;
            this.camera = camera;
            this.fade = fade;
        }
    }

    public Frame evaluate(double t) {
        Clip c = clipAt(t);
        if (c == null) return null;
        double local = t - c.timelineStartMs;
        double fade = 0;
        if (c.transitionIn == Clip.Transition.FADE && c.transitionMs > 0) {
            double half = c.transitionMs / 2.0;
            if (local < half) fade = 1 - local / half;
        }
        // fade out before a clip that fades in
        Clip next = clipStartingAt(c.timelineEndMs());
        if (next != null && next.transitionIn == Clip.Transition.FADE && next.transitionMs > 0) {
            double half = next.transitionMs / 2.0;
            double remain = c.timelineEndMs() - t;
            if (remain < half) fade = Math.max(fade, 1 - remain / half);
        }
        return new Frame(c, c.replayTimeAt(local), c.cameraAt(local), fade);
    }

    private Clip clipStartingAt(long t) {
        for (Clip c : clips) if (Math.abs(c.timelineStartMs - t) <= 1) return c;
        return null;
    }

    // ------------------------------------------------------------------ editing

    /** Appends a clip at the end of the track. */
    public void append(Clip c) {
        c.timelineStartMs = duration();
        clips.add(c);
        sort();
    }

    /** Inserts a clip at {@code t}, pushing every later clip to the right (ripple insert). */
    public void insert(Clip c, long t) {
        Clip at = clipAt(t);
        if (at != null && t > at.timelineStartMs) {
            split(at, t);
        }
        for (Clip o : clips) if (o.timelineStartMs >= t) o.timelineStartMs += c.durationMs;
        c.timelineStartMs = t;
        clips.add(c);
        sort();
    }

    /** Removes a clip and closes the gap (ripple delete). */
    public void rippleDelete(Clip c) {
        if (!clips.remove(c)) return;
        for (Clip o : clips) if (o.timelineStartMs >= c.timelineEndMs()) o.timelineStartMs -= c.durationMs;
        sort();
    }

    public void delete(Clip c) {
        clips.remove(c);
    }

    /** Cuts a clip in two at output time {@code t} (razor tool). Returns the right part. */
    public Clip split(Clip c, long t) {
        if (!c.containsTimeline(t) || t <= c.timelineStartMs + 50 || t >= c.timelineEndMs() - 50) return null;
        long local = t - c.timelineStartMs;
        Clip right = c.copy();
        right.name = c.name;
        right.timelineStartMs = t;
        right.durationMs = c.durationMs - local;
        right.sourceStartMs = c.sourceStartMs + Math.round(local * c.speed);
        right.transitionIn = Clip.Transition.CUT;
        // keep the camera motion continuous: insert the evaluated camera at the cut
        CameraState atCut = c.cameraAt(local);
        right.path.retime(1.0, -local);
        if (atCut != null) {
            right.path.add(new fr.orakun.redo.core.camera.CameraKeyframe(0, atCut,
                    fr.orakun.redo.core.math.Easing.LINEAR));
            c.path.add(new fr.orakun.redo.core.camera.CameraKeyframe(local, atCut,
                    fr.orakun.redo.core.math.Easing.LINEAR));
        }
        dropOutside(right, right.durationMs);
        c.durationMs = local;
        dropOutside(c, c.durationMs);
        clips.add(right);
        sort();
        return right;
    }

    private static void dropOutside(Clip c, long dur) {
        List<fr.orakun.redo.core.camera.CameraKeyframe> keep = new ArrayList<fr.orakun.redo.core.camera.CameraKeyframe>();
        for (fr.orakun.redo.core.camera.CameraKeyframe k : c.path.keys()) {
            if (k.timeMs >= 0 && k.timeMs <= dur) keep.add(k);
        }
        c.path.keys().clear();
        c.path.keys().addAll(keep);
    }

    /** Moves a clip to a new start; overlapping clips after it are pushed right. */
    public void move(Clip c, long newStart) {
        c.timelineStartMs = Math.max(0, newStart);
        resolveOverlaps(c);
    }

    /** Pushes clips that overlap {@code anchor} to the right of it. */
    public void resolveOverlaps(Clip anchor) {
        sort();
        long end = anchor.timelineEndMs();
        for (Clip o : clips) {
            if (o == anchor) continue;
            if (o.timelineStartMs >= anchor.timelineStartMs && o.timelineStartMs < end) {
                long shift = end - o.timelineStartMs;
                for (Clip p : clips) if (p != anchor && p.timelineStartMs >= o.timelineStartMs) p.timelineStartMs += shift;
                break;
            }
        }
        sort();
    }

    /** Changes the length of a clip from its right edge, keeping camera keyframe times. */
    public void trimEnd(Clip c, long newDuration) {
        long old = c.durationMs;
        c.durationMs = Math.max(100, newDuration);
        long delta = c.durationMs - old;
        if (delta < 0) {
            dropOutside(c, c.durationMs);
        }
        for (Clip o : clips) if (o != c && o.timelineStartMs >= c.timelineStartMs + old) o.timelineStartMs += delta;
        sort();
    }

    /** Trims the left edge: the clip starts later in the replay and later on the timeline. */
    public void trimStart(Clip c, long deltaMs) {
        long d = Math.max(-c.timelineStartMs, Math.min(deltaMs, c.durationMs - 100));
        c.timelineStartMs += d;
        c.durationMs -= d;
        c.sourceStartMs += Math.round(d * c.speed);
        c.path.retime(1.0, -d);
        dropOutside(c, c.durationMs);
        sort();
    }

    /** Removes gaps between clips. */
    public void closeGaps() {
        sort();
        long t = 0;
        for (Clip c : clips) {
            c.timelineStartMs = t;
            t += c.durationMs;
        }
    }

    // ------------------------------------------------------------------ json

    @Override
    public Object toJson() {
        Map<String, Object> m = Json.obj();
        m.put("name", name);
        m.put("hideHud", hideHud);
        m.put("hideNametags", hideNametags);
        List<Object> cl = new ArrayList<Object>();
        for (Clip c : clips) cl.add(c.toJson());
        m.put("clips", cl);
        List<Object> ml = new ArrayList<Object>();
        for (Marker k : markers) ml.add(k.toJson());
        m.put("markers", ml);
        m.put("export", export.toJson());
        return m;
    }

    public static Sequence fromJson(Map<String, Object> m) {
        Sequence s = new Sequence();
        s.name = Json.getString(m, "name", s.name);
        s.hideHud = Json.getBool(m, "hideHud", true);
        s.hideNametags = Json.getBool(m, "hideNametags", false);
        for (Map<String, Object> c : Json.objects(Json.getList(m, "clips"))) s.clips.add(Clip.fromJson(c));
        for (Map<String, Object> k : Json.objects(Json.getList(m, "markers"))) s.markers.add(Marker.fromJson(k));
        Map<String, Object> ex = Json.getObj(m, "export");
        if (ex != null) s.export = ExportSettings.fromJson(ex);
        s.sort();
        return s;
    }

    public Sequence copy() {
        return fromJson(Json.parseObject(Json.write(toJson())));
    }
}
