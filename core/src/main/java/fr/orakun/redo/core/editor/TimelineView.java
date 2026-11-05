package fr.orakun.redo.core.editor;

import fr.orakun.redo.core.director.Director;
import fr.orakun.redo.core.director.ShotType;
import fr.orakun.redo.core.timeline.Clip;
import fr.orakun.redo.core.timeline.Marker;
import fr.orakun.redo.core.timeline.Sequence;
import fr.orakun.redo.core.ui.Gfx;
import fr.orakun.redo.core.ui.Keys;
import fr.orakun.redo.core.ui.Theme;
import fr.orakun.redo.core.ui.Widget;

/** The montage track: ruler, clips, markers and playhead, with move / trim / scrub by mouse. */
public final class TimelineView extends Widget {
    private static final int RULER = 12;
    private static final int TRACK = 30;
    private static final int EDGE = 4;

    private enum Drag { NONE, SCRUB, MOVE, TRIM_START, TRIM_END }

    private final EditorSession s;
    /** Pixels per second. */
    private double scale = 20;
    /** Timeline time at the left edge, in ms. */
    private double offset;
    private boolean fitted;
    public boolean snap = true;
    private Drag drag = Drag.NONE;
    private Clip dragClip;
    private double dragGrab;
    private long dragOrigStart, dragOrigDuration;
    private double dragStartX;
    private int mods;

    public TimelineView(EditorSession s) {
        this.s = s;
    }

    public void setMods(int m) {
        mods = m;
    }

    private double toX(double ms) {
        return x + (ms - offset) / 1000.0 * scale;
    }

    private double toMs(double px) {
        return offset + (px - x) / scale * 1000.0;
    }

    public void fit() {
        double dur = Math.max(10000, s.sequence.duration() + 2000);
        scale = Math.max(2, (w - 10) / (dur / 1000.0));
        offset = 0;
    }

    public void zoom(double factor, double aroundX) {
        double at = toMs(aroundX);
        scale = Math.max(1, Math.min(400, scale * factor));
        offset = Math.max(0, at - (aroundX - x) / scale * 1000.0);
    }

    @Override
    public void render(Gfx g, int mx, int my) {
        if (!fitted && w > 0) {
            fit();
            fitted = true;
        }
        Sequence seq = s.sequence;
        g.fill(x, y, x + w, y + h, Theme.BG);
        g.pushClip(x, y, x + w, y + h);

        // ruler
        g.fill(x, y, x + w, y + RULER, Theme.HEADER);
        double stepSec = niceStep(60 / scale);
        double first = Math.floor(offset / 1000.0 / stepSec) * stepSec;
        for (double t = first; toX(t * 1000) < x + w; t += stepSec) {
            int px = (int) toX(t * 1000);
            if (px < x) continue;
            g.fill(px, y + RULER - 4, px + 1, y + RULER, Theme.TEXT_DIM);
            g.text(Director.formatTime((long) (t * 1000)), px + 2, y + 2, Theme.TEXT_DIM, false);
            double sub = stepSec / 4;
            for (int k = 1; k < 4; k++) {
                int sx = (int) toX((t + sub * k) * 1000);
                g.fill(sx, y + RULER - 2, sx + 1, y + RULER, Theme.BORDER);
            }
        }

        // clips
        int ty = y + RULER + 3;
        g.fill(x, ty - 1, x + w, ty + TRACK + 1, 0x60000000);
        for (Clip c : seq.clips) {
            int x0 = (int) toX(c.timelineStartMs), x1 = (int) toX(c.timelineEndMs());
            if (x1 < x || x0 > x + w) continue;
            ShotType t = ShotType.byName(c.shotType);
            int col = Theme.shotColor(t);
            boolean sel = c == s.selected;
            g.fill(x0, ty, Math.max(x0 + 1, x1 - 1), ty + TRACK, sel ? Theme.mix(col, 0xFFFFFFFF, 0.18) : col);
            g.fill(x0, ty, Math.max(x0 + 1, x1 - 1), ty + 2, Theme.mix(col, 0xFF000000, 0.35));
            if (c.transitionIn == Clip.Transition.FADE) {
                int fw = (int) (c.transitionMs / 2000.0 * scale);
                for (int i = 0; i < fw && x0 + i < x1; i++) {
                    g.fill(x0 + i, ty + 2, x0 + i + 1, ty + TRACK, Theme.alpha(0xFF000000, 0.5 * (1 - i / (double) Math.max(1, fw))));
                }
            }
            if (x1 - x0 > 16) {
                g.pushClip(Math.max(x, x0 + 2), ty, Math.min(x + w, x1 - 2), ty + TRACK);
                g.text(fit(g, EditorSession.clipLabel(c), x1 - x0 - 6), x0 + 4, ty + 5, Theme.TEXT, true);
                String info = Director.formatTime(c.durationMs) + (c.speed != 1 ? "  x" + trim(c.speed) : "");
                g.text(fit(g, info, x1 - x0 - 6), x0 + 4, ty + 17, Theme.alpha(Theme.TEXT, 0.75), false);
                g.popClip();
            }
            if (sel) frame(g, x0, ty, Math.max(x0 + 2, x1 - 1), ty + TRACK, Theme.ACCENT);
        }

        // markers
        int my0 = ty + TRACK + 3;
        for (Marker m : seq.markers) {
            int px = (int) toX(m.timeMs);
            g.fill(px - 1, my0, px + 2, my0 + 6, Theme.momentColor(m.kind));
        }

        // playhead
        double ph = s.previewShot == null ? s.playheadMs : -1;
        if (ph >= 0) {
            int px = (int) toX(ph);
            g.fill(px, y, px + 1, y + h, Theme.PLAYHEAD);
            g.fill(px - 3, y, px + 4, y + 4, Theme.PLAYHEAD);
        }
        if (seq.clips.isEmpty()) {
            centered(g, "Timeline vide : double-cliquez un plan du chutier, ou générez une bande-annonce.", x + w / 2, ty + 11, Theme.TEXT_DIM);
        }
        g.popClip();
    }

    private static String trim(double v) {
        String s = String.valueOf(Math.round(v * 100) / 100.0);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private static double niceStep(double minSec) {
        double[] steps = {0.5, 1, 2, 5, 10, 15, 30, 60, 120, 300, 600};
        for (double st : steps) if (st >= minSec) return st;
        return 1200;
    }

    private Clip clipAtX(double mx) {
        double t = toMs(mx);
        return s.sequence.clipAt(t);
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (!contains(mx, my)) return false;
        int ty = y + RULER + 3;
        if (my < ty || button == 2) {
            drag = Drag.SCRUB;
            s.setPlayhead(toMs(mx));
            return true;
        }
        Clip c = clipAtX(mx);
        if (c == null) {
            s.selected = null;
            drag = Drag.SCRUB;
            s.setPlayhead(toMs(mx));
            return true;
        }
        s.selected = c;
        if (button == 1) return true;
        double x0 = toX(c.timelineStartMs), x1 = toX(c.timelineEndMs());
        dragClip = c;
        dragOrigStart = c.timelineStartMs;
        dragOrigDuration = c.durationMs;
        dragStartX = mx;
        dragGrab = toMs(mx) - c.timelineStartMs;
        if (mx - x0 <= EDGE) drag = Drag.TRIM_START;
        else if (x1 - mx <= EDGE) drag = Drag.TRIM_END;
        else drag = Drag.MOVE;
        s.beginGesture();
        return true;
    }

    private long snapTime(long t, Clip ignore) {
        if (!snap || Keys.shift(mods)) return t;
        long best = t;
        double bestPx = 6;
        long[] candidates = new long[s.sequence.clips.size() * 2 + 2];
        int n = 0;
        candidates[n++] = 0;
        candidates[n++] = (long) s.playheadMs;
        for (Clip c : s.sequence.clips) {
            if (c == ignore) continue;
            candidates[n++] = c.timelineStartMs;
            candidates[n++] = c.timelineEndMs();
        }
        for (int i = 0; i < n; i++) {
            double px = Math.abs(candidates[i] - t) / 1000.0 * scale;
            if (px < bestPx) {
                bestPx = px;
                best = candidates[i];
            }
        }
        return best;
    }

    @Override
    public void mouseDrag(double mx, double my, int button) {
        switch (drag) {
            case SCRUB:
                s.setPlayhead(toMs(mx));
                break;
            case MOVE: {
                long start = Math.max(0, (long) (toMs(mx) - dragGrab));
                long snappedStart = snapTime(start, dragClip);
                long snappedEnd = snapTime(start + dragClip.durationMs, dragClip);
                if (snappedStart != start) start = snappedStart;
                else if (snappedEnd != start + dragClip.durationMs) start = snappedEnd - dragClip.durationMs;
                dragClip.timelineStartMs = Math.max(0, start);
                break;
            }
            case TRIM_END: {
                long end = snapTime((long) toMs(mx), dragClip);
                s.trimEnd(dragClip, end - dragClip.timelineStartMs);
                break;
            }
            case TRIM_START: {
                long t = snapTime((long) toMs(mx), dragClip);
                long delta = t - dragClip.timelineStartMs;
                if (dragClip.durationMs - delta >= 300) s.trimStart(dragClip, delta);
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        if (drag == Drag.MOVE && dragClip != null) {
            if (Math.abs(mx - dragStartX) < 2) {
                dragClip.timelineStartMs = dragOrigStart;
            } else {
                s.sequence.resolveOverlaps(dragClip);
                s.sequence.sort();
                s.markDirty();
            }
        }
        if ((drag == Drag.TRIM_END || drag == Drag.TRIM_START) && dragClip != null && dragClip.durationMs == dragOrigDuration) {
            // nothing changed
        }
        drag = Drag.NONE;
        dragClip = null;
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        if (!contains(mx, my)) return false;
        if (Keys.ctrl(mods)) zoom(amount > 0 ? 1.25 : 0.8, mx);
        else offset = Math.max(0, offset - amount * 60 / scale * 1000.0);
        return true;
    }

    /** Keeps the playhead visible while playing. */
    public void follow() {
        double px = toX(s.playheadMs);
        if (px > x + w - 20) offset += (px - (x + w - 60)) / scale * 1000.0;
        if (px < x) offset = Math.max(0, s.playheadMs - 1000);
    }
}
