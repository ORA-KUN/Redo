package fr.orakun.redo.core.replay;

import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.director.WorldQuery;

/**
 * What the editor needs from the running replay, implemented by each platform.
 * All calls happen on the game's main thread.
 */
public interface ReplayBackend {
    /** Length of the recording. */
    long durationMs();

    /** Current replay time, including the sub-tick part. */
    double timeMs();

    /** True while a jump backwards is being replayed from the start. */
    boolean isSeeking();

    /** Jumps to a replay time. Forward jumps are quick; backward jumps replay from the start. */
    void seek(long timeMs);

    void setPaused(boolean paused);

    boolean isPaused();

    /** Replay speed multiplier (1 = real time). */
    void setSpeed(double speed);

    double speed();

    /** Forces the camera pose, or gives control back to the free camera with null. */
    void setCamera(CameraState camera);

    /** Pose of the free camera (the spectator the user flies). */
    CameraState freeCamera();

    /** Moves the free camera. */
    void teleportFreeCamera(CameraState camera);

    /** Block access for the director, or null if the world is not loaded yet. */
    WorldQuery world();

    /** Leaves the replay and returns to the title screen. */
    void exit();
}
