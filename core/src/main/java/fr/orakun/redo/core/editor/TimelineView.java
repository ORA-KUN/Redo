package fr.orakun.redo.core.editor;

import fr.orakun.redo.core.director.Director;
import fr.orakun.redo.core.director.Moment;
import fr.orakun.redo.core.director.ShotSuggestion;
import fr.orakun.redo.core.director.ShotType;
import fr.orakun.redo.core.timeline.Clip;
import fr.orakun.redo.core.timeline.Marker;
import fr.orakun.redo.core.timeline.Sequence;
import fr.orakun.redo.core.ui.Gfx;
import fr.orakun.redo.core.ui.Keys;
import fr.orakun.redo.core.ui.Theme;
import fr.orakun.redo.core.ui.Widget;

/**
 * The montage track: ruler, clips, markers and playhead, with move / trim / scrub by mouse.
 * Below it, the "Partie" strip shows the whole recording: the moments found by the director and
 * the passages the montage uses. A click on it picks the best shot of that moment.
 */
public final class TimelineView extends Widget {
    private static final int RULER = 12;
    private static final int EDGE = 4;
    private static final int STRIP = 14;

    private enum Drag { NONE, SCRUB, MOVE, TRIM_START, TRIM_END }

    /** Shot picked on the "Partie" strip, for the owner to show in the bin (then set back to null). */
    public ShotSuggestion picked;

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

    /** Height of the clip track: taller when there is room. */
    private int track() {
        return Math.max(30, Math.min(48, h - RULER - 3 - 12 - STRIP - 2));
    }

    private int trackY() {
        return y + RULER + 3;
    }

    private int stripY() {
        return trackY() + track() + 12;
    }

    private boolean hasStrip() {
        return stripY() + STRIP <= y + h;
    }

    private static final String STRIP_LABEL = "Partie";
    private int stripLabelW = 40;

    private int stripX0() {
        return x + stripLabelW;
    }

    private int stripX1() {
        return x + w - 4;
    }

    private long sourceStart() {
        return Math.max(0, s.backend.startMs());
    }

    private long sourceEnd() {
        return Math.max(sourceStart() + 1000, s.backend.durationMs());
    }

    private int stripX(double ms) {
        double k = (ms - sourceStart()) / (double) (sourceEnd() - sourceStart());
        return stripX0() + (int) Math.round(Math.max(0, Math.min(1, k)) * (stripX1() - stripX0()));
    }

    private double stripMs(double px) {
        double k = (px - stripX0()) / (double) Math.max(1, stripX1() - stripX0());
        return sourceStart() + Math.max(0, Math.min(1, k)) * (sourceEnd() - sourceStart());
    }

    private java.util.List<Moment> moments() {
        return s.analysing ? java.util.Collections.<Moment>emptyList() : s.director.moments();
    }

    /** Moment under the strip position {@code px}, the best scored one if several. */
    private Moment momentAt(double px) {
        Moment best = null;
        for (Moment m : moments()) {
            int a = stripX(m.startMs) - 1, b = Math.max(a + 3, stripX(m.endMs) + 1);
            if (px >= a && px < b && (best == null || m.score > best.score)) best = m;
        }
        return best;
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
        final int TRACK = track();
        int ty = trackY();
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
        int stripTop = hasStrip() ? stripY() - 2 : y + h;
        if (ph >= 0) {
            int px = (int) toX(ph);
            g.fill(px, y, px + 1, stripTop, Theme.PLAYHEAD);
            g.fill(px - 3, y, px + 4, y + 4, Theme.PLAYHEAD);
        }
        if (seq.clips.isEmpty()) {
            String empty = "Timeline vide : double-cliquez un plan du chutier, ou créez une bande-annonce.";
            centered(g, fit(g, empty, w - 12), x + w / 2, ty + TRACK / 2 - 4, Theme.TEXT_DIM);
        }
        if (hasStrip()) renderStrip(g, mx, my);
        g.popClip();
    }

    private void renderStrip(Gfx g, int mx, int my) {
        int sy = stripY();
        stripLabelW = g.textWidth(STRIP_LABEL) + 10;
        g.fill(x, sy - 2, x + w, y + h, Theme.HEADER);
        g.text(STRIP_LABEL, x + 4, sy + 3, Theme.TEXT_DIM, false);
        int x0 = stripX0(), x1 = stripX1();
        g.fill(x0, sy, x1, sy + STRIP, 0x80000000);
        // moments
        Moment hover = my >= sy && my < sy + STRIP ? momentAt(mx) : null;
        for (Moment m : moments()) {
            int a = stripX(m.startMs), b = Math.max(a + 2, stripX(m.endMs));
            int col = Theme.momentColor(m.kind.label);
            g.fill(a, sy + 4, b, sy + STRIP - 1, m == hover ? Theme.mix(col, 0xFFFFFFFF, 0.3) : Theme.alpha(col, 0.8));
        }
        // passages used by the montage
        for (Clip c : s.sequence.clips) {
            int a = stripX(c.sourceStartMs), b = Math.max(a + 1, stripX(c.sourceEndMs()));
            g.fill(a, sy + 1, b, sy + 3, c == s.selected ? Theme.ACCENT : Theme.alpha(Theme.TEXT, 0.6));
        }
        // where the replay is
        int px = stripX(s.backend.timeMs());
        g.fill(px, sy, px + 1, sy + STRIP, Theme.TEXT);
    }

    @Override
    public String tooltipAt(int mx, int my) {
        if (!contains(mx, my)) return null;
        if (hasStrip() && my >= stripY() - 2) {
            Moment m = momentAt(mx);
            if (m != null) {
                String what = m.label == null || m.label.isEmpty() ? m.kind.label : m.label;
                return what + " (" + Director.formatTime(m.startMs) + " → " + Director.formatTime(m.endMs) + ") · clic : voir ses plans";
            }
            return "Toute la partie : moments repérés (couleurs) et passages du montage (traits)";
        }
        if (my < trackY()) return "Clic ou glisser : déplacer la tête de lecture";
        if (my < trackY() + track()) {
            Clip c = clipAtX(mx);
            if (c != null) return "Glisser : déplacer · bords : raccourcir · Suppr : supprimer";
        }
        return null;
    }

    /** Best shot of the moment at {@code t} (source time): the highest score among those covering it. */
    private ShotSuggestion shotAt(double t) {
        ShotSuggestion best = null;
        double bestDist = Double.MAX_VALUE;
        for (ShotSuggestion sh : s.edit.suggestions) {
            Clip c = sh.clip;
            double d = t < c.sourceStartMs ? c.sourceStartMs - t : t > c.sourceEndMs() ? t - c.sourceEndMs() : 0;
            if (best == null || d < bestDist || (d == bestDist && sh.score > best.score)) {
                best = sh;
                bestDist = d;
            }
        }
        return bestDist <= 5000 ? best : null;
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
        if (hasStrip() && my >= stripY() - 2) {
            Moment m = momentAt(mx);
            ShotSuggestion sh = shotAt(m != null ? m.peakMs : stripMs(mx));
            if (sh != null) {
                s.selectedShot = sh;
                s.previewShot(sh);
                picked = sh;
            } else {
                s.toast("Aucun plan proposé à ce moment de la partie.", false);
            }
            return true;
        }
        int ty = trackY();
        if (my >= ty + track()) {
            drag = Drag.SCRUB;
            s.setPlayhead(toMs(mx));
            return true;
        }
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
