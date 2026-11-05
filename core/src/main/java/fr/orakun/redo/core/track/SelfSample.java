package fr.orakun.redo.core.track;

/**
 * State of the recording player at a client tick. The server never sends a player its own
 * movements, so Redo stores them separately and replays them on a stand-in entity.
 */
public final class SelfSample {
    public static final int SWING = 1;
    public static final int HURT = 2;
    public static final int USING_ITEM = 4;

    public final int timeMs;
    public final double x, y, z;
    public final float yaw, pitch, headYaw, bodyYaw;
    /** {@link EntitySample} flags. */
    public final int flags;
    /** SWING / HURT / USING_ITEM. */
    public final int actions;

    public SelfSample(int timeMs, double x, double y, double z, float yaw, float pitch, float headYaw, float bodyYaw,
                      int flags, int actions) {
        this.timeMs = timeMs;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.headYaw = headYaw;
        this.bodyYaw = bodyYaw;
        this.flags = flags;
        this.actions = actions;
    }
}
