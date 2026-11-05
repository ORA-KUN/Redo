package fr.orakun.redo.core.ui;

/** A plain screen with a message (loading, errors). Escape runs the optional action. */
public final class MessageView implements View {
    private final String title;
    private final String message;
    private final Runnable onEscape;
    private final boolean opaque;

    public MessageView(String title, String message, boolean opaque, Runnable onEscape) {
        this.title = title;
        this.message = message;
        this.opaque = opaque;
        this.onEscape = onEscape;
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY, double dtMs) {
        int w = g.width(), h = g.height();
        g.fill(0, 0, w, h, opaque ? Theme.BG : 0x80000000);
        Widget.centered(g, title, w / 2, h / 2 - 12, Theme.ACCENT);
        if (message != null) Widget.centered(g, message, w / 2, h / 2 + 2, Theme.TEXT);
        if (onEscape != null) Widget.centered(g, "Échap pour revenir", w / 2, h / 2 + 20, Theme.TEXT_DIM);
    }

    @Override
    public void setModifiers(int mods) {
    }

    @Override
    public boolean mouseDown(double x, double y, int button) {
        return true;
    }

    @Override
    public void mouseDrag(double x, double y, int button, double dx, double dy) {
    }

    @Override
    public void mouseUp(double x, double y, int button) {
    }

    @Override
    public boolean scroll(double x, double y, double amount) {
        return true;
    }

    @Override
    public boolean key(int key, int mods) {
        if (key == Keys.ESCAPE && onEscape != null) {
            onEscape.run();
        }
        return true;
    }

    @Override
    public boolean chr(char c) {
        return false;
    }

    @Override
    public boolean typing() {
        return false;
    }

    @Override
    public boolean showsWorld() {
        return !opaque;
    }

    @Override
    public void closed() {
    }
}
