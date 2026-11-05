package fr.orakun.redo.core.track;

import fr.orakun.redo.core.math.Vec3d;

/** Position and state of an entity at a moment of the recording. */
public final class EntitySample {
    public static final int SNEAK = 1;
    public static final int SPRINT = 2;
    public static final int ELYTRA = 4;
    public static final int ON_GROUND = 8;
    public static final int SWIMMING = 16;
    public static final int RIDING = 32;
    public static final int INVISIBLE = 64;
    public static final int SLEEPING = 128;

    public final int timeMs;
    public final float x, y, z;
    public final float yaw, pitch;
    public final int flags;

    public EntitySample(int timeMs, float x, float y, float z, float yaw, float pitch, int flags) {
        this.timeMs = timeMs;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.flags = flags;
    }

    public Vec3d pos() {
        return new Vec3d(x, y, z);
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }
}
