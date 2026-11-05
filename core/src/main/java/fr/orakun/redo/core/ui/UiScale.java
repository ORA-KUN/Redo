package fr.orakun.redo.core.ui;

import fr.orakun.redo.core.Redo;

/**
 * Size of the Redo screens, independent of the game's interface size: the editor is laid out for
 * at least 640 x 360 interface pixels, so a large game interface size no longer squeezes it.
 */
public final class UiScale {
    public static final int MIN_WIDTH = 640;
    public static final int MIN_HEIGHT = 360;

    private UiScale() {
    }

    /** Screen pixels per interface pixel for a window of this size (a whole number, at least 1). */
    public static int pixels(int windowWidth, int windowHeight) {
        int forced = Redo.config().uiScale;
        int auto = Math.min(windowWidth / MIN_WIDTH, windowHeight / MIN_HEIGHT);
        int s = forced > 0 ? forced : auto;
        // never so large that the screen is smaller than the editor needs
        while (s > 1 && (windowWidth / s < MIN_WIDTH * 3 / 4 || windowHeight / s < MIN_HEIGHT * 3 / 4)) s--;
        return Math.max(1, s);
    }
}
