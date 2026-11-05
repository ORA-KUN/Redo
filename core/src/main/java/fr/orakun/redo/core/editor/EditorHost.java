package fr.orakun.redo.core.editor;

import java.io.File;

/** Services the editor needs from the platform screen. */
public interface EditorHost {
    String clipboard();

    void openFolder(File folder);

    /** Closes the editor screen and lets the user fly the free camera. */
    void enterFreeCamera();

    /** Hides the editor while the sequence plays full screen (Esc comes back). */
    void enterFullscreen();

    /** Leaves the replay. */
    void exitReplay();

    int framebufferWidth();

    int framebufferHeight();
}
