package fr.orakun.redo.core.record;

import fr.orakun.redo.core.Redo;
import fr.orakun.redo.core.format.RecordingSession;
import fr.orakun.redo.core.ui.Gfx;
import fr.orakun.redo.core.ui.Keys;
import fr.orakun.redo.core.ui.Theme;

/** The small "REC" indicator drawn over the game while recording. */
public final class RecorderHud {
    private RecorderHud() {
    }

    public static void render(Gfx g) {
        String msg = Recorder.recentMessage();
        RecordingSession s = Recorder.session();
        int y = 4;
        if (s != null && !s.isStopped() && Redo.config().showRecordingIndicator) {
            int t = s.timeMs() / 1000;
            String time = String.format("%d:%02d", t / 60, t % 60);
            boolean blink = (System.currentTimeMillis() / 600) % 2 == 0;
            String label = "REC " + time;
            if (s.highlightOpen()) label += "  ★ passage à garder (" + Keys.name(Redo.config().keyHighlight) + ")";
            int w = g.textWidth(label) + 16;
            g.fill(4, y, 4 + w, y + 13, 0x80000000);
            g.fill(8, y + 4, 13, y + 9, blink ? 0xFFE53935 : 0x80E53935);
            g.text(label, 16, y + 3, s.highlightOpen() ? Theme.ACCENT : 0xFFFFFFFF, true);
            y += 16;
        }
        if (msg != null) {
            int w = g.textWidth(msg) + 8;
            g.fill(4, y, 4 + w, y + 13, 0x80000000);
            g.text(msg, 8, y + 3, 0xFFFFFFFF, true);
        }
    }
}
