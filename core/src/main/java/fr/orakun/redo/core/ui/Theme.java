package fr.orakun.redo.core.ui;

import fr.orakun.redo.core.director.ShotType;

/** Colours of the Redo interface (night blue and warm orange). */
public final class Theme {
    public static final int BG = 0xF00E1220;
    public static final int PANEL = 0xEA141A2B;
    public static final int PANEL_LIGHT = 0xF01C2338;
    public static final int HEADER = 0xFF1A2135;
    public static final int BORDER = 0xFF2A3350;
    public static final int TEXT = 0xFFECF0FA;
    public static final int TEXT_DIM = 0xFF8A93A8;
    public static final int TEXT_DARK = 0xFF0E1220;
    public static final int ACCENT = 0xFFF2A65A;
    public static final int ACCENT_DARK = 0xFFB8732F;
    public static final int BUTTON = 0xFF262E47;
    public static final int BUTTON_HOVER = 0xFF323C5C;
    public static final int BUTTON_ON = 0xFF4A3A2A;
    public static final int FIELD = 0xFF0A0D18;
    public static final int RED = 0xFFE5534B;
    public static final int GREEN = 0xFF57B36A;
    public static final int PLAYHEAD = 0xFFFF5A4E;
    public static final int WIDE = 0xFF3D6FB6;
    public static final int MEDIUM = 0xFF3F9C68;
    public static final int TIGHT = 0xFFB0558A;
    public static final int MANUAL = 0xFF8C6BC8;

    private Theme() {
    }

    public static int shotColor(ShotType t) {
        if (t == null) return MANUAL;
        switch (t.size) {
            case WIDE: return WIDE;
            case TIGHT: return TIGHT;
            default: return MEDIUM;
        }
    }

    public static int momentColor(String kind) {
        if (kind == null) return TEXT_DIM;
        if (kind.equals("kill")) return 0xFFE5534B;
        if (kind.equals("combat")) return 0xFFF2A65A;
        if (kind.equals("course")) return 0xFFE7C04F;
        if (kind.equals("construction")) return 0xFF57B36A;
        if (kind.equals("décor") || kind.equals("ouverture")) return 0xFF4F9BE7;
        if (kind.equals("explosion")) return 0xFFFF7A2F;
        if (kind.equals("marqueur")) return 0xFFC77DFF;
        return 0xFF9AA4BC;
    }

    public static int alpha(int argb, double a) {
        int al = (int) Math.max(0, Math.min(255, ((argb >>> 24) & 0xFF) * a));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        int al = (int) (((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }
}
