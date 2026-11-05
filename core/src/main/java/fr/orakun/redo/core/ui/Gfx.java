package fr.orakun.redo.core.ui;

/**
 * Drawing surface implemented by each platform. Coordinates are editor units (about 360 units tall,
 * whatever the window size), colours are ARGB.
 */
public interface Gfx {
    int width();

    int height();

    void fill(int x0, int y0, int x1, int y1, int argb);

    /** Draws text with its top-left corner at (x, y). */
    void text(String s, int x, int y, int argb, boolean shadow);

    int textWidth(String s);

    /** Height of a text line, usually 9. */
    int lineHeight();

    /** Restricts drawing to a rectangle until {@link #popClip()}. Clips nest. */
    void pushClip(int x0, int y0, int x1, int y1);

    void popClip();
}
