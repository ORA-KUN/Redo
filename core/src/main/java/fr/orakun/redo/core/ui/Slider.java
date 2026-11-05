package fr.orakun.redo.core.ui;

import java.util.Locale;

/** Horizontal value slider with a label; drag or scroll to change. */
public class Slider extends Widget {
    public interface Listener {
        void changed(double value, boolean released);
    }

    public final String label;
    public double min, max, step;
    public double value;
    public String unit = "";
    public int decimals = 1;
    public Listener listener;
    private boolean dragging;

    public Slider(String label, double min, double max, double step, double value, Listener listener) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = value;
        this.listener = listener;
    }

    public Slider unit(String u, int decimals) {
        this.unit = u;
        this.decimals = decimals;
        return this;
    }

    public String valueText() {
        return String.format(Locale.ROOT, "%." + decimals + "f", value) + unit;
    }

    @Override
    public void render(Gfx g, int mx, int my) {
        if (!visible) return;
        boolean hover = enabled && contains(mx, my);
        g.fill(x, y, x + w, y + h, hover || dragging ? Theme.BUTTON_HOVER : Theme.BUTTON);
        double t = max > min ? (value - min) / (max - min) : 0;
        int fx = x + (int) Math.round(t * w);
        g.fill(x, y + h - 2, fx, y + h, enabled ? Theme.ACCENT : Theme.TEXT_DIM);
        g.fill(Math.max(x, fx - 1), y, Math.min(x + w, fx + 1), y + h, Theme.alpha(Theme.ACCENT, 0.8));
        int ty = y + (h - g.lineHeight()) / 2 + 1;
        g.text(fit(g, label, w / 2), x + 3, ty, enabled ? Theme.TEXT_DIM : Theme.alpha(Theme.TEXT_DIM, 0.6), false);
        String v = valueText();
        g.text(v, x + w - 3 - g.textWidth(v), ty, enabled ? Theme.TEXT : Theme.TEXT_DIM, false);
    }

    private void setFromMouse(double mx, boolean released) {
        double t = Math.max(0, Math.min(1, (mx - x) / (double) w));
        set(min + t * (max - min), released);
    }

    public void set(double v, boolean released) {
        if (step > 0) v = Math.round(v / step) * step;
        v = Math.max(min, Math.min(max, v));
        boolean changed = v != value;
        value = v;
        if (listener != null && (changed || released)) listener.changed(value, released);
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (!enabled || !contains(mx, my) || button != 0) return false;
        dragging = true;
        setFromMouse(mx, false);
        return true;
    }

    @Override
    public void mouseDrag(double mx, double my, int button) {
        if (dragging) setFromMouse(mx, false);
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            setFromMouse(mx, true);
        }
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        if (!enabled || !contains(mx, my)) return false;
        set(value + Math.signum(amount) * (step > 0 ? step : (max - min) / 50), true);
        return true;
    }
}
