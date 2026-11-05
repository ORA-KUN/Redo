package fr.orakun.redo.core.ui;

/** Flat button; can act as a toggle when {@link #on} is used. */
public class Button extends Widget {
    public String label;
    public Runnable action;
    public boolean on;
    public boolean accent;

    public Button(String label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    public Button tip(String t) {
        this.tooltip = t;
        return this;
    }

    public Button accent() {
        this.accent = true;
        return this;
    }

    @Override
    public void render(Gfx g, int mx, int my) {
        if (!visible) return;
        boolean hover = enabled && contains(mx, my);
        int bg = accent ? (hover ? Theme.ACCENT : Theme.ACCENT_DARK) : on ? Theme.BUTTON_ON : hover ? Theme.BUTTON_HOVER : Theme.BUTTON;
        if (!enabled) bg = Theme.alpha(bg, 0.5);
        g.fill(x, y, x + w, y + h, bg);
        if (on) g.fill(x, y + h - 1, x + w, y + h, Theme.ACCENT);
        int color = !enabled ? Theme.TEXT_DIM : accent ? Theme.TEXT_DARK : Theme.TEXT;
        String s = fit(g, label, w - 4);
        g.text(s, x + (w - g.textWidth(s)) / 2, y + (h - g.lineHeight()) / 2 + 1, color, false);
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (!enabled || !contains(mx, my) || button != 0) return false;
        if (action != null) action.run();
        return true;
    }
}
