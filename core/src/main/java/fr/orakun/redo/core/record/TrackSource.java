package fr.orakun.redo.core.record;

/**
 * Read access to the client world while recording, implemented by each platform. Everything is
 * called on the game's main thread, once per client tick.
 */
public interface TrackSource {
    /** Reusable description of one entity, filled by the platform. */
    final class EntityState {
        public int id;
        public String uuid = "";
        public String name = "";
        /** Registry name, e.g. "minecraft:zombie". */
        public String type = "";
        public boolean player;
        public double x, y, z;
        public float yaw, pitch, headYaw, bodyYaw;
        /** {@link fr.orakun.redo.core.track.EntitySample} flags. */
        public int flags;
        /** Hurt animation running (hurtTime > 0). */
        public boolean hurt;
        /** Dying (health at zero / death animation). */
        public boolean dead;
        /** Arm swing animation running. */
        public boolean swinging;
        public boolean usingItem;

        public void reset() {
            id = 0;
            uuid = name = type = "";
            player = hurt = dead = swinging = usingItem = false;
            x = y = z = 0;
            yaw = pitch = headYaw = bodyYaw = 0;
            flags = 0;
        }
    }

    interface Visitor {
        void visit(EntityState e);
    }

    /** False while no world is loaded (loading screens, disconnects). */
    boolean inWorld();

    /** Fills the state of the recording player. Returns false if there is none. */
    boolean self(EntityState out);

    /** Visits every loaded entity except the recording player. The state object is reused. */
    void entities(Visitor v);

    /** Items of the recording player: main hand, off hand, head, chest, legs, feet ("" = empty). */
    String[] equipment();

    /** Y of the highest non-air block at (x, z), or {@link Integer#MIN_VALUE} if not loaded. */
    int height(int x, int z);

    /** Registry name of the block at this position ("minecraft:grass_block"). */
    String block(int x, int y, int z);
}
