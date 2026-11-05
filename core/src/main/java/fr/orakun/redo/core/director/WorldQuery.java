package fr.orakun.redo.core.director;

import fr.orakun.redo.core.math.Vec3d;

/**
 * Access to the blocks of the world, provided by the platform when the replay world is loaded.
 * Without it the director falls back to the coarse terrain samples of the track.
 */
public interface WorldQuery {
    /** True if the block at this position stops the camera (solid or liquid surface). */
    boolean isSolid(double x, double y, double z);

    /** True if the segment between the two points crosses a solid block. */
    boolean isBlocked(Vec3d from, Vec3d to);

    /** True if the chunk containing (x, z) is loaded, i.e. the answers above are meaningful. */
    boolean isLoaded(double x, double z);

    /**
     * Registry name of the block ("minecraft:oak_log"), "minecraft:air" for air, or null where the
     * world is not loaded or the platform cannot tell (the scenery is then not analysed).
     */
    String blockName(int x, int y, int z);

    /** Height of the highest block of the column (x, z), or Integer.MIN_VALUE if not loaded. */
    int surfaceY(int x, int z);

    /** Lowest block height of the world (0 on 1.8.9, -64 in the overworld of recent versions). */
    int minY();
}
