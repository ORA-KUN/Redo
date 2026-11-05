package fr.orakun.redo.core.ui;

/** Key codes used by the core, equal to GLFW's. The 1.8.9 adapter translates LWJGL 2 codes. */
public final class Keys {
    public static final int UNKNOWN = -1;
    public static final int SPACE = 32;
    public static final int APOSTROPHE = 39;
    public static final int COMMA = 44;
    public static final int MINUS = 45;
    public static final int PERIOD = 46;
    public static final int SLASH = 47;
    public static final int N0 = 48;
    public static final int N9 = 57;
    public static final int SEMICOLON = 59;
    public static final int EQUAL = 61;
    public static final int A = 65;
    public static final int C = 67;
    public static final int D = 68;
    public static final int E = 69;
    public static final int F = 70;
    public static final int H = 72;
    public static final int I = 73;
    public static final int J = 74;
    public static final int K = 75;
    public static final int L = 76;
    public static final int M = 77;
    public static final int N = 78;
    public static final int O = 79;
    public static final int P = 80;
    public static final int Q = 81;
    public static final int R = 82;
    public static final int S = 83;
    public static final int V = 86;
    public static final int W = 87;
    public static final int X = 88;
    public static final int Y = 89;
    public static final int Z = 90;
    public static final int ESCAPE = 256;
    public static final int ENTER = 257;
    public static final int TAB = 258;
    public static final int BACKSPACE = 259;
    public static final int INSERT = 260;
    public static final int DELETE = 261;
    public static final int RIGHT = 262;
    public static final int LEFT = 263;
    public static final int DOWN = 264;
    public static final int UP = 265;
    public static final int PAGE_UP = 266;
    public static final int PAGE_DOWN = 267;
    public static final int HOME = 268;
    public static final int END = 269;
    public static final int F1 = 290;
    public static final int F9 = 298;
    public static final int F12 = 301;
    public static final int KP_SUBTRACT = 333;
    public static final int KP_ADD = 334;
    public static final int KP_ENTER = 335;
    public static final int LEFT_SHIFT = 340;
    public static final int LEFT_CONTROL = 341;
    public static final int LEFT_ALT = 342;
    public static final int RIGHT_SHIFT = 344;
    public static final int RIGHT_CONTROL = 345;
    public static final int RIGHT_ALT = 346;

    public static final int MOD_SHIFT = 1;
    public static final int MOD_CONTROL = 2;
    public static final int MOD_ALT = 4;
    /** Cmd on macOS. Treated like Control for shortcuts. */
    public static final int MOD_SUPER = 8;

    private Keys() {
    }

    public static boolean ctrl(int mods) {
        return (mods & (MOD_CONTROL | MOD_SUPER)) != 0;
    }

    public static boolean shift(int mods) {
        return (mods & MOD_SHIFT) != 0;
    }

    public static String name(int key) {
        if (key >= A && key <= Z) return String.valueOf((char) key);
        if (key >= N0 && key <= N9) return String.valueOf((char) key);
        if (key >= F1 && key <= F12 + 13) return "F" + (key - F1 + 1);
        switch (key) {
            case SPACE: return "Espace";
            case ENTER: return "Entrée";
            case TAB: return "Tab";
            case ESCAPE: return "Échap";
            case DELETE: return "Suppr";
            case BACKSPACE: return "Retour";
            case LEFT: return "←";
            case RIGHT: return "→";
            case UP: return "↑";
            case DOWN: return "↓";
            case HOME: return "Début";
            case END: return "Fin";
            case UNKNOWN: return "Aucune";
            default: return "#" + key;
        }
    }

    /**
     * Translates an LWJGL 2 key code (Minecraft 1.8.9) to a GLFW code.
     */
    public static int fromLwjgl2(int k) {
        switch (k) {
            case 1: return ESCAPE;
            case 14: return BACKSPACE;
            case 15: return TAB;
            case 28: return ENTER;
            case 156: return KP_ENTER;
            case 29: return LEFT_CONTROL;
            case 157: return RIGHT_CONTROL;
            case 42: return LEFT_SHIFT;
            case 54: return RIGHT_SHIFT;
            case 56: return LEFT_ALT;
            case 184: return RIGHT_ALT;
            case 57: return SPACE;
            case 199: return HOME;
            case 207: return END;
            case 200: return UP;
            case 208: return DOWN;
            case 203: return LEFT;
            case 205: return RIGHT;
            case 201: return PAGE_UP;
            case 209: return PAGE_DOWN;
            case 210: return INSERT;
            case 211: return DELETE;
            case 12: return MINUS;
            case 13: return EQUAL;
            case 51: return COMMA;
            case 52: return PERIOD;
            case 74: return KP_SUBTRACT;
            case 78: return KP_ADD;
            default:
                break;
        }
        if (k >= 59 && k <= 68) return F1 + (k - 59);
        if (k == 87) return F1 + 10;
        if (k == 88) return F1 + 11;
        if (k >= 2 && k <= 10) return N0 + (k - 1);
        if (k == 11) return N0;
        String row1 = "QWERTYUIOP", row2 = "ASDFGHJKL", row3 = "ZXCVBNM";
        if (k >= 16 && k <= 25) return row1.charAt(k - 16);
        if (k >= 30 && k <= 38) return row2.charAt(k - 30);
        if (k >= 44 && k <= 50) return row3.charAt(k - 44);
        return UNKNOWN;
    }

    /** Inverse of {@link #fromLwjgl2(int)} for the keys Redo binds. */
    public static int toLwjgl2(int key) {
        for (int k = 0; k < 256; k++) if (fromLwjgl2(k) == key) return k;
        return 0;
    }
}
