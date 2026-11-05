package fr.orakun.redo.core.ui;

/** Button that cycles through a list of choices (left click forward, right click back). */
public class Cycle<T> extends Button {
    public interface Namer<T> {
        String name(T value);
    }

    public interface Listener<T> {
        void changed(T value);
    }

    public final String prefix;
    public final T[] values;
    public int index;
    private final Namer<T> namer;
    private final Listener<T> listener;

    public Cycle(String prefix, T[] values, T current, Namer<T> namer, Listener<T> listener) {
        super("", null);
        this.prefix = prefix;
        this.values = values;
        this.namer = namer;
        this.listener = listener;
        set(current);
    }

    public void set(T v) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(v)) index = i;
        label = (prefix.isEmpty() ? "" : prefix + " : ") + namer.name(values[index]);
    }

    public T value() {
        return values[index];
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (!enabled || !contains(mx, my) || (button != 0 && button != 1)) return false;
        index = (index + (button == 0 ? 1 : values.length - 1)) % values.length;
        set(values[index]);
        if (listener != null) listener.changed(values[index]);
        return true;
    }
}
