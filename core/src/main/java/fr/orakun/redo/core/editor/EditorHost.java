package fr.orakun.redo.core.editor;

import java.io.File;

/** Services the editor needs from the platform screen. */
public interface EditorHost {
    String clipboard();

    void openFolder(File folder);

    /**
     * Reads the player's movement keys (as bound in the game options) into
     * {forward, strafe right, up, sprint}, each -1, 0 or 1.
     */
    void movement(double[] out);

    /** Leaves the replay. */
    void exitReplay();

    int framebufferWidth();

    int framebufferHeight();

    /** True if captured frames are bottom-up (raw OpenGL reads), false if already top-down. */
    boolean framesBottomUp();
}
