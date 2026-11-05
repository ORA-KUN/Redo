package fr.orakun.redo.core.ui;

/** Single-line text input with cursor, selection-less editing and a placeholder. */
public class TextField extends Widget {
    public interface Listener {
        void submit(String text);
    }

    private final StringBuilder text = new StringBuilder();
    public String placeholder = "";
    public int maxLength = 300;
    public boolean focused;
    public boolean password;
    public Listener onSubmit;
    private int cursor;
    private int scroll;
    private long blink;

    public String text() {
        return text.toString();
    }

    public void setText(String s) {
        text.setLength(0);
        text.append(s == null ? "" : s);
        cursor = text.length();
        scroll = 0;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void focusChanged(boolean f) {
        focused = f;
        blink = System.currentTimeMillis();
    }

    private String shown() {
        if (!password) return text.toString();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) sb.append(i < text.length() - 4 ? '•' : text.charAt(i));
        return sb.toString();
    }

    @Override
    public void render(Gfx g, int mx, int my) {
        if (!visible) return;
        g.fill(x, y, x + w, y + h, Theme.FIELD);
        frame(g, x, y, x + w, y + h, focused ? Theme.ACCENT : Theme.BORDER);
        int ty = y + (h - g.lineHeight()) / 2 + 1;
        int inner = w - 8;
        String s = shown();
        if (s.isEmpty() && !focused) {
            g.text(fit(g, placeholder, inner), x + 4, ty, Theme.TEXT_DIM, false);
            return;
        }
        if (cursor < scroll) scroll = cursor;
        while (scroll < cursor && g.textWidth(s.substring(scroll, cursor)) > inner) scroll++;
        int end = scroll;
        while (end < s.length() && g.textWidth(s.substring(scroll, end + 1)) <= inner) end++;
        g.pushClip(x + 2, y, x + w - 2, y + h);
        g.text(s.substring(scroll, end), x + 4, ty, Theme.TEXT, false);
        if (focused && ((System.currentTimeMillis() - blink) / 500) % 2 == 0) {
            int cx = x + 4 + g.textWidth(s.substring(scroll, Math.min(cursor, end)));
            g.fill(cx, ty - 1, cx + 1, ty + g.lineHeight() - 1, Theme.ACCENT);
        }
        g.popClip();
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (!contains(mx, my)) return false;
        cursor = text.length();
        blink = System.currentTimeMillis();
        return true;
    }

    @Override
    public boolean key(int key, int mods) {
        if (!focused) return false;
        blink = System.currentTimeMillis();
        switch (key) {
            case Keys.BACKSPACE:
                if (Keys.ctrl(mods)) {
                    int p = wordLeft();
                    text.delete(p, cursor);
                    cursor = p;
                } else if (cursor > 0) {
                    text.deleteCharAt(--cursor);
                }
                return true;
            case Keys.DELETE:
                if (cursor < text.length()) text.deleteCharAt(cursor);
                return true;
            case Keys.LEFT:
                cursor = Keys.ctrl(mods) ? wordLeft() : Math.max(0, cursor - 1);
                return true;
            case Keys.RIGHT:
                cursor = Keys.ctrl(mods) ? wordRight() : Math.min(text.length(), cursor + 1);
                return true;
            case Keys.HOME:
                cursor = 0;
                return true;
            case Keys.END:
                cursor = text.length();
                return true;
            case Keys.ENTER:
            case Keys.KP_ENTER:
                if (onSubmit != null) onSubmit.submit(text.toString());
                return true;
            default:
                // swallow letter shortcuts while typing
                return key >= Keys.SPACE && key < Keys.ESCAPE;
        }
    }

    private int wordLeft() {
        int p = cursor;
        while (p > 0 && text.charAt(p - 1) == ' ') p--;
        while (p > 0 && text.charAt(p - 1) != ' ') p--;
        return p;
    }

    private int wordRight() {
        int p = cursor;
        while (p < text.length() && text.charAt(p) == ' ') p++;
        while (p < text.length() && text.charAt(p) != ' ') p++;
        return p;
    }

    @Override
    public boolean chr(char c) {
        if (!focused || c < 32 || c == 127) return false;
        if (text.length() >= maxLength) return true;
        text.insert(cursor++, c);
        return true;
    }

    /** Pastes text at the cursor (the platform reads the clipboard). */
    public void paste(String s) {
        if (s == null) return;
        for (char c : s.replace('\n', ' ').toCharArray()) chr(c);
    }
}
