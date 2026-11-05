package fr.orakun.redo.core.track;

import fr.orakun.redo.core.math.Vec3d;

/** Something that happened during the recording, used by the automatic director. */
public final class GameEvent {
    public enum Type {
        DAMAGE, DEATH, SWING, BLOCK_PLACE, BLOCK_BREAK, EXPLOSION, CHAT, MARKER, DIMENSION, PROJECTILE,
        HIGHLIGHT_START, HIGHLIGHT_END, TITLE;

        public static Type byId(int id) {
            Type[] v = values();
            return id >= 0 && id < v.length ? v[id] : CHAT;
        }
    }

    public static final int NONE = Integer.MIN_VALUE;

    public final Type type;
    public final int timeMs;
    /** Main entity (victim, actor...). */
    public final int entity;
    /** Secondary entity (attacker, killer...). */
    public final int other;
    public final float x, y, z;
    public final String text;

    public GameEvent(Type type, int timeMs, int entity, int other, float x, float y, float z, String text) {
        this.type = type;
        this.timeMs = timeMs;
        this.entity = entity;
        this.other = other;
        this.x = x;
        this.y = y;
        this.z = z;
        this.text = text == null ? "" : text;
    }

    public Vec3d pos() {
        return new Vec3d(x, y, z);
    }
}
