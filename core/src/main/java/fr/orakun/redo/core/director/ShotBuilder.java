package fr.orakun.redo.core.director;

import fr.orakun.redo.core.camera.CameraKeyframe;
import fr.orakun.redo.core.camera.CameraPath;
import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.math.Angles;
import fr.orakun.redo.core.math.Easing;
import fr.orakun.redo.core.math.Vec3d;
import fr.orakun.redo.core.timeline.Clip;
import fr.orakun.redo.core.track.EntityInfo;
import fr.orakun.redo.core.track.EventTrack;
import fr.orakun.redo.core.track.SelfSample;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Turns a shot type + a moment into a clip with camera keyframes. Tracking shots are baked
 * as dense keyframes following the smoothed subject trajectory; fixed shots use a few
 * keyframes with soft easing.
 */
public final class ShotBuilder {
    private static final double EYE = 1.62;
    private static final double CHEST = 1.15;
    private static final long STEP = 250;

    private final EventTrack track;
    private final WorldQuery world;

    /** Share of the shot where the subject was hidden by blocks (computed when a world is available). */
    public double lastOcclusion;

    public ShotBuilder(EventTrack track, WorldQuery world) {
        this.track = track;
        this.world = world;
    }

    public Clip build(ShotType type, Moment m, ShotParams p, Random rng) {
        long dur = p.durationMs > 0 ? p.durationMs : type.defaultDurationMs;
        double speed = !Double.isNaN(p.speed) ? p.speed : defaultSpeed(type);
        int subject = p.subject != Integer.MIN_VALUE ? p.subject : m.mainSubject();
        Vec3d focus = p.focus != null ? p.focus : m.focus;
        int side = p.side != 0 ? p.side : (rng.nextBoolean() ? 1 : -1);
        long cover = Math.round(dur * speed);

        long source;
        if (p.sourceStartMs >= 0) {
            source = p.sourceStartMs;
        } else if (type == ShotType.SLOW_MOTION) {
            source = m.peakMs - Math.round(cover * 0.7);
        } else if (m.kind == Moment.Kind.COMBAT || m.kind == Moment.Kind.CHASE || m.kind == Moment.Kind.BUILD) {
            long span = m.endMs - m.startMs;
            source = span > cover ? m.startMs + (long) ((span - cover) * rng.nextDouble() * 0.5) : m.peakMs - cover / 2;
        } else {
            source = m.peakMs - Math.round(cover * 0.55);
        }
        // nothing to film before the recording has a world (its first moment): no frozen start
        long first = Math.max(0, track.startMs());
        source = Math.max(first, Math.min(source, Math.max(first, track.durationMs() - cover)));

        if (subject != Integer.MIN_VALUE && track.positionAt(subject, source + cover / 2.0) == null) {
            subject = Integer.MIN_VALUE; // no data for this entity then: film the place instead
        }
        if (focus == null && subject != Integer.MIN_VALUE) focus = track.positionAt(subject, source);
        if (focus == null) return null;

        Clip c = new Clip();
        c.shotType = type.name();
        c.durationMs = dur;
        c.sourceStartMs = source;
        c.speed = speed;
        c.subjectEntity = subject;
        c.name = type.label + (m.label == null || m.label.isEmpty() ? "" : " · " + m.label);
        Context ctx = new Context(c, subject, focus, p, side, rng);
        lastOcclusion = 0;
        if (subject == Integer.MIN_VALUE && type.needsSubject()) {
            buildPlace(ShotType.ESTABLISHING, ctx);
        } else {
            switch (type) {
                case ESTABLISHING: buildPlace(type, ctx); break;
                case DRONE: buildDrone(ctx); break;
                case PAN: buildPan(ctx); break;
                case ORBIT: buildOrbit(ctx, 6.5, 2.2, 140, 0); break;
                case DUTCH: buildOrbit(ctx, 5.0, 1.6, 50, 14 * side); break;
                case SLOW_MOTION: buildOrbit(ctx, 4.0, 1.4, 70, 0); break;
                case TIMELAPSE: buildOrbit(ctx, 18, 10, 120, 0); break;
                case FOLLOW: buildFollow(ctx); break;
                case TRUCK: buildTruck(ctx); break;
                case DOLLY_IN: buildDolly(ctx, 13, 4.5, 1.8, 1.4); break;
                case DOLLY_OUT: buildDolly(ctx, 3.5, 15, 1.4, 5.5); break;
                case CRANE: buildCrane(ctx); break;
                case LOW_ANGLE: buildAngle(ctx, 3.8, 0.25, 35); break;
                case HIGH_ANGLE: buildAngle(ctx, 4.5, 8.5, 25); break;
                case OVER_SHOULDER: buildShoulder(ctx); break;
                case CLOSE_UP: buildCloseUp(ctx); break;
                case POV: buildPov(ctx); break;
                default: buildOrbit(ctx, 6, 2, 90, 0);
            }
        }
        if (c.path.isEmpty()) return null;
        return c;
    }

    static double defaultSpeed(ShotType t) {
        switch (t) {
            case SLOW_MOTION: return 0.3;
            case TIMELAPSE: return 8;
            default: return 1;
        }
    }

    // ---------------------------------------------------------------- context

    private final class Context {
        final Clip clip;
        final int subject;
        final Vec3d focus;
        final ShotParams p;
        final int side;
        final Random rng;
        final double fov;

        Context(Clip clip, int subject, Vec3d focus, ShotParams p, int side, Random rng) {
            this.clip = clip;
            this.subject = subject;
            this.focus = focus;
            this.p = p;
            this.side = side;
            this.rng = rng;
            this.fov = !Double.isNaN(p.fov) ? p.fov : 70;
        }

        double replay(long local) {
            return clip.sourceStartMs + local * clip.speed;
        }

        Vec3d subjectAt(long local) {
            if (subject == Integer.MIN_VALUE) return focus;
            Vec3d s = track.smoothPositionAt(subject, replay(local), 500);
            return s == null ? focus : s;
        }

        Vec3d directionAt(long local) {
            if (subject == Integer.MIN_VALUE) return new Vec3d(0, 0, 1);
            double t = replay(local);
            Vec3d a = track.positionAt(subject, t - 600);
            Vec3d b = track.positionAt(subject, t + 600);
            if (a != null && b != null) {
                Vec3d d = new Vec3d(b.x - a.x, 0, b.z - a.z);
                if (d.length() > 0.8) return d.normalize();
            }
            double yaw = track.yawAt(subject, t);
            return Vec3d.fromYawPitch(yaw, 0);
        }

        double dist(double d) {
            return d * p.distanceScale;
        }

        double height(double h) {
            return h * p.heightScale;
        }

        long duration() {
            return clip.durationMs;
        }
    }

    private void key(Context c, long local, Vec3d cam, Vec3d target, double roll, Easing e) {
        Vec3d safe = safe(cam, target, c.replay(local));
        c.clip.path.add(new CameraKeyframe(local, CameraState.lookAt(safe, target, roll, c.fov), e));
    }

    // ---------------------------------------------------------------- places

    private void buildPlace(ShotType type, Context c) {
        Vec3d f = c.focus;
        double angle = c.rng.nextDouble() * Math.PI * 2;
        double dist = c.dist(32 + c.rng.nextDouble() * 14);
        double up = c.height(16 + c.rng.nextDouble() * 8);
        Vec3d dir = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
        Vec3d start = f.add(dir.mul(dist)).add(0, up, 0);
        Vec3d end = f.add(dir.mul(dist * 0.85)).add(perp(dir).mul(4 * c.side * c.p.motion)).add(0, up * 0.9, 0);
        key(c, 0, start, f.add(0, 2, 0), 0, Easing.SMOOTH);
        key(c, c.duration(), end, f.add(0, 2, 0), 0, Easing.LINEAR);
    }

    private void buildDrone(Context c) {
        Vec3d f = c.focus;
        Vec3d dir = c.subject != Integer.MIN_VALUE ? c.directionAt(0) : Vec3d.fromYawPitch(c.rng.nextDouble() * 360, 0);
        double h = c.height(22);
        double half = c.dist(26) * c.p.motion;
        Vec3d a = f.sub(dir.mul(half)).add(0, h, 0);
        Vec3d b = f.add(dir.mul(half * 0.6)).add(0, h + 3, 0);
        long d = c.duration();
        for (long t = 0; t <= d; t += 500) {
            double u = Easing.EASE_IN_OUT.apply(t / (double) d);
            Vec3d cam = a.lerp(b, u);
            Vec3d look = cam.add(dir.mul(18)).add(0, -h * 0.9, 0);
            if (c.subject != Integer.MIN_VALUE) look = look.lerp(c.subjectAt(t), 0.5);
            key(c, t, cam, look, 0, Easing.LINEAR);
        }
    }

    private void buildPan(Context c) {
        Vec3d f = c.focus;
        Vec3d cam = f.add(0, c.height(6), 0);
        double ground = track.groundAt(cam.x, cam.z, c.replay(0));
        if (!Double.isNaN(ground)) cam = new Vec3d(cam.x, Math.max(cam.y, ground + 6), cam.z);
        double yaw0 = c.rng.nextDouble() * 360;
        double sweep = 75 * c.side * c.p.motion;
        CameraState s0 = new CameraState(cam.x, cam.y, cam.z, yaw0, 12, 0, c.fov);
        CameraState s1 = new CameraState(cam.x, cam.y, cam.z, yaw0 + sweep, 8, 0, c.fov);
        c.clip.path.add(new CameraKeyframe(0, s0, Easing.SMOOTH));
        c.clip.path.add(new CameraKeyframe(c.duration(), s1, Easing.LINEAR));
    }

    // ---------------------------------------------------------------- subject

    private void buildOrbit(Context c, double radius, double height, double sweepDeg, double roll) {
        long d = c.duration();
        Vec3d dir0 = c.directionAt(0);
        double base = Math.atan2(dir0.z, dir0.x) + Math.PI / 2 * c.side; // start from the side
        double sweep = Math.toRadians(sweepDeg) * c.side * c.p.motion;
        double r = c.dist(radius);
        double h = c.height(height);
        List<Vec3d> cams = new ArrayList<Vec3d>();
        List<Vec3d> targets = new ArrayList<Vec3d>();
        for (long t = 0; t <= d; t += STEP) {
            double u = Easing.EASE_IN_OUT.apply(t / (double) d) * 0.85 + (t / (double) d) * 0.15;
            double a = base + sweep * u;
            Vec3d s = c.subjectAt(t);
            cams.add(s.add(Math.cos(a) * r, h, Math.sin(a) * r));
            targets.add(s.add(0, CHEST, 0));
        }
        bake(c, cams, targets, roll);
    }

    private void buildFollow(Context c) {
        long d = c.duration();
        List<Vec3d> cams = new ArrayList<Vec3d>();
        List<Vec3d> targets = new ArrayList<Vec3d>();
        for (long t = 0; t <= d; t += STEP) {
            Vec3d s = c.subjectAt(t);
            Vec3d dir = c.directionAt(t);
            cams.add(s.sub(dir.mul(c.dist(5.0))).add(0, c.height(2.1), 0));
            targets.add(c.subjectAt(t + 400).add(dir.mul(2)).add(0, EYE, 0));
        }
        bake(c, cams, targets, 0);
    }

    private void buildTruck(Context c) {
        long d = c.duration();
        List<Vec3d> cams = new ArrayList<Vec3d>();
        List<Vec3d> targets = new ArrayList<Vec3d>();
        for (long t = 0; t <= d; t += STEP) {
            Vec3d s = c.subjectAt(t);
            Vec3d dir = c.directionAt(t);
            Vec3d side = perp(dir).mul(c.side * c.dist(6.0));
            cams.add(s.add(side).add(dir.mul(-0.8)).add(0, c.height(1.5), 0));
            targets.add(s.add(dir.mul(0.6)).add(0, CHEST, 0));
        }
        bake(c, cams, targets, 0);
    }

    private void buildDolly(Context c, double fromDist, double toDist, double fromH, double toH) {
        long d = c.duration();
        Vec3d dir0 = c.directionAt(0);
        double a = Math.atan2(dir0.z, dir0.x) + Math.toRadians(25 * c.side);
        Vec3d out = new Vec3d(Math.cos(a), 0, Math.sin(a));
        List<Vec3d> cams = new ArrayList<Vec3d>();
        List<Vec3d> targets = new ArrayList<Vec3d>();
        for (long t = 0; t <= d; t += STEP) {
            double u = Easing.EASE_IN_OUT.apply(t / (double) d);
            double dist = c.dist(Angles.lerp(fromDist, toDist, u));
            double h = c.height(Angles.lerp(fromH, toH, u));
            Vec3d s = c.subjectAt(t);
            cams.add(s.add(out.mul(dist)).add(0, h, 0));
            targets.add(s.add(0, CHEST, 0));
        }
        bake(c, cams, targets, 0);
    }

    private void buildCrane(Context c) {
        long d = c.duration();
        Vec3d dir0 = c.directionAt(0);
        double a = Math.atan2(dir0.z, dir0.x) + Math.toRadians(150 * c.side);
        Vec3d out = new Vec3d(Math.cos(a), 0, Math.sin(a));
        List<Vec3d> cams = new ArrayList<Vec3d>();
        List<Vec3d> targets = new ArrayList<Vec3d>();
        for (long t = 0; t <= d; t += STEP) {
            double u = Easing.SMOOTH.apply(t / (double) d);
            Vec3d s = c.subjectAt(t);
            double dist = c.dist(Angles.lerp(5, 11, u));
            double h = c.height(Angles.lerp(0.6, 11, u * c.p.motion));
            cams.add(s.add(out.mul(dist)).add(0, h, 0));
            targets.add(s.add(0, CHEST, 0));
        }
        bake(c, cams, targets, 0);
    }

    private void buildAngle(Context c, double dist, double height, double sweepDeg) {
        buildOrbit(c, dist, height, sweepDeg, 0);
    }

    private void buildShoulder(Context c) {
        long d = c.duration();
        int opponent = opponentOf(c);
        List<Vec3d> cams = new ArrayList<Vec3d>();
        List<Vec3d> targets = new ArrayList<Vec3d>();
        for (long t = 0; t <= d; t += STEP) {
            Vec3d s = c.subjectAt(t);
            Vec3d eye = s.add(0, EYE, 0);
            Vec3d look;
            if (opponent != Integer.MIN_VALUE) {
                Vec3d o = track.smoothPositionAt(opponent, c.replay(t), 400);
                look = o != null ? o.add(0, CHEST, 0) : eye.add(Vec3d.fromYawPitch(track.yawAt(c.subject, c.replay(t)), 5).mul(6));
            } else {
                look = eye.add(Vec3d.fromYawPitch(track.yawAt(c.subject, c.replay(t)), 5).mul(6));
            }
            Vec3d fwd = new Vec3d(look.x - eye.x, 0, look.z - eye.z).normalize();
            Vec3d right = perp(fwd).mul(-c.side);
            cams.add(eye.sub(fwd.mul(c.dist(1.9))).add(right.mul(0.75)).add(0, 0.25, 0));
            targets.add(look);
        }
        bake(c, cams, targets, 0);
    }

    private void buildCloseUp(Context c) {
        long d = c.duration();
        List<Vec3d> cams = new ArrayList<Vec3d>();
        List<Vec3d> targets = new ArrayList<Vec3d>();
        for (long t = 0; t <= d; t += STEP) {
            Vec3d s = c.subjectAt(t);
            Vec3d eye = s.add(0, EYE - 0.05, 0);
            double yaw = track.yawAt(c.subject, c.replay(t));
            Vec3d fwd = Vec3d.fromYawPitch(yaw, 0);
            double u = t / (double) d;
            double dist = c.dist(2.4 - 0.7 * u);
            cams.add(eye.add(fwd.mul(dist)).add(perp(fwd).mul(0.5 * c.side)).add(0, 0.1, 0));
            targets.add(eye);
        }
        bake(c, cams, targets, 0);
        c.clip.path.keys().get(0).state = c.clip.path.keys().get(0).state.withFov(55);
        for (CameraKeyframe k : c.clip.path.keys()) k.state = k.state.withFov(55);
    }

    private void buildPov(Context c) {
        long d = c.duration();
        boolean self = c.subject == track.selfId() && !track.self().isEmpty();
        for (long t = 0; t <= d; t += 100) {
            double rt = c.replay(t);
            double x, y, z, yaw, pitch;
            if (self) {
                SelfSample s = selfAt(rt);
                x = s.x;
                y = s.y + EYE;
                z = s.z;
                yaw = s.yaw;
                pitch = s.pitch;
            } else {
                Vec3d p = track.positionAt(c.subject, rt);
                if (p == null) continue;
                x = p.x;
                y = p.y + EYE;
                z = p.z;
                yaw = track.yawAt(c.subject, rt);
                fr.orakun.redo.core.track.EntitySample es = track.sampleAt(c.subject, rt);
                pitch = es == null ? 0 : es.pitch;
            }
            c.clip.path.add(new CameraKeyframe(t, new CameraState(x, y, z, yaw, pitch, 0, c.fov), Easing.LINEAR));
        }
        c.clip.path.interpolation = CameraPath.Interpolation.LINEAR;
    }

    private SelfSample selfAt(double t) {
        List<SelfSample> l = track.self();
        int lo = 0, hi = l.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (l.get(mid).timeMs <= t) lo = mid;
            else hi = mid - 1;
        }
        return l.get(lo);
    }

    private int opponentOf(Context c) {
        double t = c.replay(c.duration() / 2);
        Vec3d s = c.subjectAt(c.duration() / 2);
        int best = Integer.MIN_VALUE;
        double bestD = 8;
        for (EntityInfo e : track.entities()) {
            if (e.id == c.subject) continue;
            Vec3d o = track.positionAt(e.id, t);
            if (o == null) continue;
            double dd = o.distance(s);
            if (dd < bestD && (e.player || e.isHostileMob())) {
                bestD = dd;
                best = e.id;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- baking

    /** Smooths sampled camera positions/targets and stores them as keyframes. */
    private void bake(Context c, List<Vec3d> cams, List<Vec3d> targets, double roll) {
        List<Vec3d> sc = smooth(cams, 2);
        List<Vec3d> st = smooth(targets, 2);
        int blocked = 0;
        for (int i = 0; i < sc.size(); i++) {
            long t = Math.min(c.duration(), i * STEP);
            Vec3d cam = safe(sc.get(i), st.get(i), c.replay(t));
            if (world != null && world.isLoaded(cam.x, cam.z) && world.isBlocked(cam, st.get(i))) blocked++;
            c.clip.path.add(new CameraKeyframe(t, CameraState.lookAt(cam, st.get(i), roll, c.fov), Easing.LINEAR));
        }
        lastOcclusion = sc.isEmpty() ? 0 : blocked / (double) sc.size();
    }

    private static List<Vec3d> smooth(List<Vec3d> in, int radius) {
        List<Vec3d> out = new ArrayList<Vec3d>(in.size());
        for (int i = 0; i < in.size(); i++) {
            Vec3d sum = Vec3d.ZERO;
            double w = 0;
            for (int k = -radius; k <= radius; k++) {
                int j = Math.max(0, Math.min(in.size() - 1, i + k));
                double wk = radius + 1 - Math.abs(k);
                sum = sum.add(in.get(j).mul(wk));
                w += wk;
            }
            out.add(sum.mul(1 / w));
        }
        return out;
    }

    /** Keeps the camera above the ground and out of blocks. */
    Vec3d safe(Vec3d cam, Vec3d target, double t) {
        double g = track.groundAt(cam.x, cam.z, t);
        if (!Double.isNaN(g) && cam.y < g + 1.0) cam = new Vec3d(cam.x, g + 1.0, cam.z);
        if (world != null && world.isLoaded(cam.x, cam.z)) {
            int guard = 0;
            while (world.isSolid(cam.x, cam.y, cam.z) && guard++ < 12) {
                Vec3d toTarget = target.sub(cam);
                double len = toTarget.length();
                if (len < 1.2) {
                    cam = cam.add(0, 1, 0);
                } else {
                    cam = cam.add(toTarget.normalize().mul(0.6)).add(0, 0.25, 0);
                }
            }
        }
        return cam;
    }

    static Vec3d perp(Vec3d dir) {
        return new Vec3d(-dir.z, 0, dir.x);
    }
}
