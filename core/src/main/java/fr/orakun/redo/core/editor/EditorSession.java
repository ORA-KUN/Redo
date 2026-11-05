package fr.orakun.redo.core.editor;

import fr.orakun.redo.core.Redo;
import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.camera.CameraKeyframe;
import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.director.Director;
import fr.orakun.redo.core.director.Moment;
import fr.orakun.redo.core.director.ShotParams;
import fr.orakun.redo.core.director.ShotSuggestion;
import fr.orakun.redo.core.director.ShotType;
import fr.orakun.redo.core.director.TrailerBuilder;
import fr.orakun.redo.core.export.ExportJob;
import fr.orakun.redo.core.export.FfmpegLocator;
import fr.orakun.redo.core.format.EditFile;
import fr.orakun.redo.core.format.RedoArchive;
import fr.orakun.redo.core.math.Easing;
import fr.orakun.redo.core.prompt.ClaudeClient;
import fr.orakun.redo.core.prompt.PromptService;
import fr.orakun.redo.core.replay.ReplayBackend;
import fr.orakun.redo.core.timeline.Clip;
import fr.orakun.redo.core.timeline.EditHistory;
import fr.orakun.redo.core.timeline.Sequence;
import fr.orakun.redo.core.track.EventTrack;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/**
 * State and actions of the editor for one opened recording, independent of any interface:
 * the bin of shots, the sequence being edited, playback, the free camera, prompt and export.
 */
public final class EditorSession {
    public enum Mode { MONTAGE, FREE_CAMERA, FULLSCREEN }

    public static final class Toast {
        public final String text;
        public final boolean error;
        public final long at = System.currentTimeMillis();

        Toast(String text, boolean error) {
            this.text = text;
            this.error = error;
        }
    }

    public final RedoArchive archive;
    public final EventTrack track;
    public final ReplayBackend backend;
    public final Director director;
    public final EditFile edit;
    public final EditHistory history = new EditHistory();
    private final ConcurrentLinkedQueue<Runnable> mainQueue = new ConcurrentLinkedQueue<Runnable>();
    private final Executor mainThread = new Executor() {
        @Override
        public void execute(Runnable command) {
            mainQueue.add(command);
        }
    };

    public Mode mode = Mode.MONTAGE;
    public Sequence sequence;
    public double playheadMs;
    public boolean playing;
    public Clip selected;
    public ShotSuggestion selectedShot;
    /** Shot of the bin being previewed on its own, or null. */
    public ShotSuggestion previewShot;
    private double previewMs;
    private Clip lastClip;
    private boolean seekPending;
    private long lastSeekRequest;
    public boolean promptBusy;
    public String promptMessage = "";
    public ExportJob export;
    public final List<Toast> toasts = new ArrayList<Toast>();
    private long seed = System.nanoTime();
    private boolean dirty;
    private long lastSave = System.currentTimeMillis();
    public boolean analysing;

    /** Keyframes placed with the free camera: replay time + pose. */
    public final List<double[]> pendingKeyTimes = new ArrayList<double[]>();
    public final List<CameraState> pendingKeys = new ArrayList<CameraState>();
    public TrailerBuilder.Style trailerStyle = TrailerBuilder.Style.CINEMATIC;

    public EditorSession(RedoArchive archive, EventTrack track, ReplayBackend backend) {
        this.archive = archive;
        this.track = track;
        this.backend = backend;
        this.director = new Director(track, archive.meta, backend.world());
        this.edit = EditFile.load(archive.editFile());
        this.sequence = edit.activeSequence();
    }

    // ------------------------------------------------------------------ lifecycle

    /** Runs the automatic director the first time the recording is opened. */
    public void analyseIfNeeded() {
        if (!edit.analysed) analyse();
    }

    public void analyse() {
        analysing = true;
        try {
            List<ShotSuggestion> keep = new ArrayList<ShotSuggestion>();
            for (ShotSuggestion s : edit.suggestions) if (s.favorite || !"auto".equals(s.origin)) keep.add(s);
            List<ShotSuggestion> fresh = director.analyse(seed++);
            edit.suggestions.clear();
            edit.suggestions.addAll(keep);
            edit.suggestions.addAll(fresh);
            edit.analysed = true;
            if (sequence.clips.isEmpty() && !fresh.isEmpty()) {
                Sequence trailer = TrailerBuilder.build(fresh, trailerStyle, "Bande-annonce auto");
                if (!trailer.clips.isEmpty()) {
                    sequence = trailer;
                    edit.sequences.set(edit.active, trailer);
                }
            }
            toast(fresh.size() + " plans proposés, " + director.moments().size() + " moments repérés.", false);
            markDirty();
        } catch (RuntimeException e) {
            RedoLog.error("Analysis failed", e);
            toast("L'analyse a échoué : " + e, true);
        } finally {
            analysing = false;
        }
    }

    /** Called once per rendered frame on the main thread. */
    public void frame(double dtMs) {
        Runnable r;
        while ((r = mainQueue.poll()) != null) r.run();
        dtMs = Math.max(0, Math.min(dtMs, 250));
        if (export != null && export.isActive()) return;

        if (mode == Mode.FREE_CAMERA) {
            backend.setCamera(null);
        } else if (previewShot != null) {
            driveSingle(previewShot.clip, dtMs);
        } else {
            driveTimeline(dtMs);
        }
        while (!toasts.isEmpty() && System.currentTimeMillis() - toasts.get(0).at > 5000) toasts.remove(0);
        if (dirty && System.currentTimeMillis() - lastSave > 3000) save();
    }

    private void driveTimeline(double dtMs) {
        if (playing && !backend.isSeeking() && !seekPending) {
            playheadMs += dtMs;
            if (playheadMs >= sequence.duration()) {
                playheadMs = sequence.duration();
                playing = false;
                if (mode == Mode.FULLSCREEN) mode = Mode.MONTAGE;
            }
        }
        Sequence.Frame f = sequence.evaluate(Math.min(playheadMs, Math.max(0, sequence.duration() - 1)));
        apply(f == null ? null : f.clip, f == null ? -1 : f.replayTimeMs, f == null ? null : f.camera);
    }

    private void driveSingle(Clip c, double dtMs) {
        if (playing && !backend.isSeeking() && !seekPending) {
            previewMs += dtMs;
            if (previewMs >= c.durationMs) previewMs = 0; // loop
        }
        apply(c, c.replayTimeAt(previewMs), c.cameraAt(previewMs));
    }

    private void apply(Clip clip, double replayTime, CameraState camera) {
        if (clip == null) {
            backend.setPaused(true);
            if (camera == null) backend.setCamera(null);
            return;
        }
        backend.setCamera(camera);
        double now = backend.timeMs();
        boolean jumped = clip != lastClip || Math.abs(now - replayTime) > (playing ? 600 : 40);
        lastClip = clip;
        if (backend.isSeeking()) return;
        if (jumped && replayTime >= 0) {
            long t = System.currentTimeMillis();
            if (playing || t - lastSeekRequest > 120) {
                backend.seek((long) replayTime);
                lastSeekRequest = t;
                seekPending = false;
            } else {
                seekPending = true;
            }
        } else if (seekPending && System.currentTimeMillis() - lastSeekRequest > 120) {
            backend.seek((long) replayTime);
            lastSeekRequest = System.currentTimeMillis();
            seekPending = false;
        }
        backend.setSpeed(clip.speed);
        backend.setPaused(!playing);
    }

    // ------------------------------------------------------------------ playback

    public void togglePlay() {
        if (!playing && previewShot == null && playheadMs >= sequence.duration() - 1) playheadMs = 0;
        playing = !playing;
    }

    public void setPlayhead(double t) {
        previewShot = null;
        playheadMs = Math.max(0, Math.min(t, sequence.duration()));
    }

    public void step(double deltaMs) {
        setPlayhead(playheadMs + deltaMs);
    }

    public void previewShot(ShotSuggestion s) {
        previewShot = s;
        previewMs = 0;
        playing = true;
    }

    public void stopPreview() {
        previewShot = null;
    }

    public double previewTime() {
        return previewMs;
    }

    public void setMode(Mode m) {
        if (m == mode) return;
        mode = m;
        if (m == Mode.FREE_CAMERA) {
            playing = false;
            previewShot = null;
            backend.setCamera(null);
            backend.setPaused(true);
            backend.setSpeed(1);
        } else if (m == Mode.FULLSCREEN) {
            previewShot = null;
            playing = true;
        }
    }

    // ------------------------------------------------------------------ editing

    private void change() {
        history.push(sequence);
    }

    public void markDirty() {
        dirty = true;
    }

    public void insertShot(ShotSuggestion s) {
        change();
        Clip c = s.clip.copy();
        if (c.name == null || c.name.isEmpty() || c.name.equals("Plan")) {
            ShotType t = s.type();
            c.name = t != null ? t.label : "Plan manuel";
        }
        long at = (long) playheadMs;
        Clip under = sequence.clipAt(at);
        if (under != null) at = under.timelineEndMs();
        sequence.insert(c, at);
        selected = c;
        previewShot = null;
        playheadMs = c.timelineStartMs;
        markDirty();
    }

    public void appendShot(ShotSuggestion s) {
        change();
        Clip c = s.clip.copy();
        sequence.append(c);
        selected = c;
        markDirty();
    }

    public void deleteSelected(boolean ripple) {
        if (selected == null) return;
        change();
        if (ripple) sequence.rippleDelete(selected);
        else sequence.delete(selected);
        selected = null;
        markDirty();
    }

    public void splitAtPlayhead() {
        Clip c = sequence.clipAt(playheadMs);
        if (c == null) return;
        long t = (long) playheadMs;
        if (t - c.timelineStartMs < 200 || c.timelineEndMs() - t < 200) return;
        change();
        Clip right = sequence.split(c, t);
        selected = right != null ? right : c;
        markDirty();
    }

    public void moveClip(Clip c, long newStart, boolean commit) {
        if (commit) change();
        sequence.move(c, Math.max(0, newStart));
        markDirty();
    }

    public void beginGesture() {
        change();
    }

    public void trimEnd(Clip c, long newDuration) {
        sequence.trimEnd(c, Math.max(300, newDuration));
        markDirty();
    }

    public void trimStart(Clip c, long deltaMs) {
        sequence.trimStart(c, deltaMs);
        markDirty();
    }

    public void closeGaps() {
        change();
        sequence.closeGaps();
        markDirty();
    }

    public void undo() {
        if (!history.canUndo()) return;
        sequence = history.undo(sequence);
        edit.sequences.set(edit.active, sequence);
        selected = null;
        markDirty();
    }

    public void redo() {
        if (!history.canRedo()) return;
        sequence = history.redo(sequence);
        edit.sequences.set(edit.active, sequence);
        selected = null;
        markDirty();
    }

    public void setSpeed(Clip c, double speed) {
        change();
        c.speed = Math.max(0.05, Math.min(16, speed));
        markDirty();
    }

    public void setTransition(Clip c, Clip.Transition t) {
        change();
        c.transitionIn = t;
        markDirty();
    }

    public void setTransitionMs(Clip c, long ms) {
        c.transitionMs = Math.max(100, Math.min(4000, ms));
        markDirty();
    }

    /** Rebuilds the selected clip with the same type on the same moment, with another seed. */
    public void variant(Clip c) {
        ShotType type = ShotType.byName(c.shotType);
        if (type == null) {
            toast("Variante impossible sur un plan manuel.", true);
            return;
        }
        ShotSuggestion s = regenerate(c, type);
        if (s == null) return;
        change();
        replaceClip(c, s.clip);
        toast("Nouvelle variante de « " + type.label + " ».", false);
    }

    /** Rebuilds the selected clip with another shot type. */
    public void changeType(Clip c, ShotType type) {
        ShotSuggestion s = regenerate(c, type);
        if (s == null) return;
        change();
        replaceClip(c, s.clip);
    }

    private ShotSuggestion regenerate(Clip c, ShotType type) {
        long mid = c.sourceStartMs + (c.sourceEndMs() - c.sourceStartMs) / 2;
        Moment m = director.momentAt(mid, c.subjectEntity);
        m.startMs = (int) c.sourceStartMs;
        m.endMs = (int) c.sourceEndMs();
        ShotParams p = new ShotParams();
        p.durationMs = c.durationMs;
        p.speed = c.speed;
        p.sourceStartMs = c.sourceStartMs;
        if (c.subjectEntity != Integer.MIN_VALUE) p.subject = c.subjectEntity;
        ShotSuggestion s = director.generate(type, m, p, seed++);
        if (s == null) toast("Impossible de construire ce plan ici (rien à filmer).", true);
        return s;
    }

    private void replaceClip(Clip old, Clip fresh) {
        fresh.timelineStartMs = old.timelineStartMs;
        fresh.transitionIn = old.transitionIn;
        fresh.transitionMs = old.transitionMs;
        fresh.name = ShotType.byName(fresh.shotType) != null ? ShotType.byName(fresh.shotType).label : old.name;
        int i = sequence.clips.indexOf(old);
        if (i < 0) return;
        sequence.clips.set(i, fresh);
        if (fresh.durationMs != old.durationMs) sequence.resolveOverlaps(fresh);
        selected = fresh;
        markDirty();
    }

    /** Replaces the sequence with a new automatic trailer. */
    public void buildTrailer(TrailerBuilder.Style style) {
        List<ShotSuggestion> pool = new ArrayList<ShotSuggestion>(edit.suggestions);
        if (pool.isEmpty()) {
            toast("Aucun plan dans le chutier : lancez l'analyse.", true);
            return;
        }
        change();
        Sequence s = TrailerBuilder.build(pool, style, "Bande-annonce " + style.label.toLowerCase());
        s.export = sequence.export;
        sequence = s;
        edit.sequences.set(edit.active, s);
        playheadMs = 0;
        selected = null;
        toast("Bande-annonce " + style.label.toLowerCase() + " : " + s.clips.size() + " plans, " + Director.formatTime(s.duration()) + ".", false);
        markDirty();
    }

    public void newSequence() {
        Sequence s = new Sequence();
        s.name = "Montage " + (edit.sequences.size() + 1);
        edit.sequences.add(s);
        edit.active = edit.sequences.size() - 1;
        sequence = s;
        history.clear();
        playheadMs = 0;
        selected = null;
        markDirty();
    }

    public void switchSequence(int index) {
        if (index < 0 || index >= edit.sequences.size()) return;
        edit.active = index;
        sequence = edit.sequences.get(index);
        history.clear();
        playheadMs = 0;
        selected = null;
    }

    public void toggleFavorite(ShotSuggestion s) {
        s.favorite = !s.favorite;
        markDirty();
    }

    public void removeShot(ShotSuggestion s) {
        edit.suggestions.remove(s);
        if (selectedShot == s) selectedShot = null;
        if (previewShot == s) previewShot = null;
        markDirty();
    }

    // ------------------------------------------------------------------ free camera keyframes

    public void addKeyframe() {
        CameraState cam = backend.freeCamera();
        if (cam == null) return;
        pendingKeys.add(cam);
        pendingKeyTimes.add(new double[]{backend.timeMs()});
        toast("Image clé " + pendingKeys.size() + " à " + Director.formatTime((long) backend.timeMs()) + ".", false);
    }

    public void clearKeyframes() {
        pendingKeys.clear();
        pendingKeyTimes.clear();
    }

    /** Turns the keyframes placed with the free camera into a shot of the bin. */
    public ShotSuggestion finishManualShot() {
        if (pendingKeys.isEmpty()) {
            toast("Placez au moins une image clé (K).", true);
            return null;
        }
        double t0 = pendingKeyTimes.get(0)[0];
        for (double[] t : pendingKeyTimes) t0 = Math.min(t0, t[0]);
        Clip c = new Clip();
        c.name = "Plan manuel";
        c.shotType = "MANUAL";
        c.sourceStartMs = (long) t0;
        c.speed = 1;
        long last = 0;
        for (int i = 0; i < pendingKeys.size(); i++) {
            long local = (long) (pendingKeyTimes.get(i)[0] - t0);
            last = Math.max(last, local);
            c.path.add(new CameraKeyframe(local, pendingKeys.get(i), Easing.SMOOTH));
        }
        if (pendingKeys.size() == 1 || last < 500) {
            // a single pose: a still shot of 4 seconds
            CameraState s = pendingKeys.get(pendingKeys.size() - 1);
            c.path.keys().clear();
            c.path.add(new CameraKeyframe(0, s, Easing.LINEAR));
            c.path.add(new CameraKeyframe(4000, s, Easing.LINEAR));
            last = 4000;
        }
        c.durationMs = last;
        ShotSuggestion s = new ShotSuggestion(c, 1, "Plan manuel · " + Director.formatTime((long) t0), "manuel");
        s.origin = "manual";
        edit.suggestions.add(0, s);
        clearKeyframes();
        markDirty();
        toast("Plan manuel ajouté au chutier (" + Director.formatTime(last) + ").", false);
        return s;
    }

    // ------------------------------------------------------------------ prompt

    public void runPrompt(String text) {
        if (text == null || text.trim().isEmpty() || promptBusy) return;
        promptBusy = true;
        promptMessage = "Je prépare les plans…";
        String key = Redo.config().claudeApiKey;
        ClaudeClient client = key == null || key.trim().isEmpty() ? null
                : new ClaudeClient(key, Redo.config().claudeModel, Redo.userAgent());
        new PromptService(director, archive.meta).run(text.trim(), client, seed++, mainThread, new PromptService.Callback() {
            @Override
            public void done(PromptService.Result r) {
                promptBusy = false;
                for (int i = r.shots.size() - 1; i >= 0; i--) {
                    ShotSuggestion s = r.shots.get(i);
                    s.origin = "prompt";
                    edit.suggestions.add(0, s);
                }
                if (r.trailer != null && !r.trailer.clips.isEmpty()) {
                    change();
                    r.trailer.export = sequence.export;
                    edit.sequences.add(r.trailer);
                    edit.active = edit.sequences.size() - 1;
                    sequence = r.trailer;
                    playheadMs = 0;
                    selected = null;
                } else if (!r.shots.isEmpty()) {
                    selectedShot = r.shots.get(0);
                    previewShot(r.shots.get(0));
                }
                promptMessage = r.message;
                if (r.warning != null) toast(r.warning, true);
                markDirty();
            }
        });
    }

    // ------------------------------------------------------------------ export

    public File ffmpeg() {
        return FfmpegLocator.find(Redo.config().ffmpegPath, Redo.home());
    }

    public File nextExportFile() {
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String base = RedoArchive.sanitize(archive.displayName() + " - " + sequence.name) + " " + stamp;
        return new File(Redo.exportDir(), base + sequence.export.format.extension);
    }

    /** Starts exporting the current sequence; the platform then renders frame by frame. */
    public boolean startExport(int framebufferW, int framebufferH) {
        if (sequence.clips.isEmpty()) {
            toast("La timeline est vide.", true);
            return false;
        }
        File ff = ffmpeg();
        if (ff == null) {
            toast("ffmpeg introuvable : installez-le depuis le panneau d'export.", true);
            return false;
        }
        playing = false;
        previewShot = null;
        mode = Mode.MONTAGE;
        File out = nextExportFile();
        export = new ExportJob(sequence.copy(), sequence.export.copy(), ff, out, framebufferW, framebufferH);
        if (!export.start()) {
            toast(export.error(), true);
            export = null;
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ misc

    public void toast(String text, boolean error) {
        toasts.add(new Toast(text, error));
        if (toasts.size() > 4) toasts.remove(0);
    }

    public Executor mainThread() {
        return mainThread;
    }

    public void save() {
        edit.sequences.set(edit.active, sequence);
        edit.save(archive.editFile());
        dirty = false;
        lastSave = System.currentTimeMillis();
    }

    public void close() {
        if (export != null) export.cancel();
        save();
        backend.setCamera(null);
    }

    /** Helper used by the timeline to show where clips come from. */
    public static String clipLabel(Clip c) {
        return c.name == null || c.name.isEmpty() ? "Plan" : c.name;
    }
}
