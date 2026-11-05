package fr.orakun.redo.core.ui;

/** A full-screen Redo interface shown by the platform's screen (editor, recordings list...). */
public interface View {
    /** Draws the view. Called every frame; {@code dtMs} is the time since the previous frame. */
    void render(Gfx g, int mouseX, int mouseY, double dtMs);

    /** Current modifier keys ({@link Keys#MOD_SHIFT}...), given before each mouse event. */
    void setModifiers(int mods);

    boolean mouseDown(double x, double y, int button);

    void mouseDrag(double x, double y, int button, double dx, double dy);

    void mouseUp(double x, double y, int button);

    boolean scroll(double x, double y, double amount);

    /** Key press with GLFW codes (see {@link Keys}). Returns true if used. */
    boolean key(int key, int mods);

    boolean chr(char c);

    /** True while a text field has the keyboard. */
    boolean typing();

    /** True if the world should be visible behind the view (replay views). */
    boolean showsWorld();

    /** Called when the platform screen closes. */
    void closed();
}
