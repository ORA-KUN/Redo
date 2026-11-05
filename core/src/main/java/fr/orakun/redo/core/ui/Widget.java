package fr.orakun.redo.core.ui;

/** Base of the Redo interface elements. Positions are set by the layout of the owning view. */
public abstract class Widget {
    public int x, y, w, h;
    public boolean visible = true;
    public boolean enabled = true;
    public String tooltip;

    public Widget bounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public boolean contains(double mx, double my) {
        return visible && mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public abstract void render(Gfx g, int mx, int my);

    /** Returns true if the click was used. */
    public boolean mouseDown(double mx, double my, int button) {
        return false;
    }

    public void mouseDrag(double mx, double my, int button) {
    }

    public void mouseUp(double mx, double my, int button) {
    }

    public boolean scroll(double mx, double my, double amount) {
        return false;
    }

    public boolean key(int key, int mods) {
        return false;
    }

    public boolean chr(char c) {
        return false;
    }

    public boolean focusable() {
        return false;
    }

    public void focusChanged(boolean focused) {
    }

    // ------------------------------------------------------------ drawing helpers

    public static void frame(Gfx g, int x0, int y0, int x1, int y1, int argb) {
        g.fill(x0, y0, x1, y0 + 1, argb);
        g.fill(x0, y1 - 1, x1, y1, argb);
        g.fill(x0, y0, x0 + 1, y1, argb);
        g.fill(x1 - 1, y0, x1, y1, argb);
    }

    /** Text cut with "…" to fit {@code maxW}. */
    public static String fit(Gfx g, String s, int maxW) {
        if (s == null) return "";
        if (g.textWidth(s) <= maxW) return s;
        String e = "…";
        int ew = g.textWidth(e);
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (g.textWidth(s.substring(0, mid)) + ew <= maxW) lo = mid;
            else hi = mid - 1;
        }
        return s.substring(0, lo) + e;
    }

    public static void centered(Gfx g, String s, int cx, int y, int argb) {
        g.text(s, cx - g.textWidth(s) / 2, y, argb, false);
    }
}
