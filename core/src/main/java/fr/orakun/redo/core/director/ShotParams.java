package fr.orakun.redo.core.director;

import fr.orakun.redo.core.math.Vec3d;

/** Tunable inputs of a shot. Unset values (NaN / MIN_VALUE / null) are chosen by the director. */
public final class ShotParams {
    public long durationMs = -1;
    public double speed = Double.NaN;
    /** Replay time at which the shot starts, or -1 to let the director centre it on the moment. */
    public long sourceStartMs = -1;
    public int subject = Integer.MIN_VALUE;
    public Vec3d focus;
    /** Multiplies distances to the subject (1 = default). */
    public double distanceScale = 1;
    /** Multiplies camera height above the subject (1 = default). */
    public double heightScale = 1;
    /** 0.5 = slow move, 1 = normal, 2 = fast move. */
    public double motion = 1;
    /** +1 or -1: orbit direction, side of a lateral travelling... 0 = random. */
    public int side;
    public double fov = Double.NaN;

    public ShotParams copy() {
        ShotParams p = new ShotParams();
        p.durationMs = durationMs;
        p.speed = speed;
        p.sourceStartMs = sourceStartMs;
        p.subject = subject;
        p.focus = focus;
        p.distanceScale = distanceScale;
        p.heightScale = heightScale;
        p.motion = motion;
        p.side = side;
        p.fov = fov;
        return p;
    }
}
