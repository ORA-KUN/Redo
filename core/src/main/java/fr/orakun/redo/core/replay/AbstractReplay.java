package fr.orakun.redo.core.replay;

import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.format.PacketRecord;
import fr.orakun.redo.core.format.PacketStreamReader;
import fr.orakun.redo.core.format.RedoArchive;
import fr.orakun.redo.core.record.Recorder;

import java.io.IOException;

/**
 * The platform-independent half of a running replay: packet scheduling, replay time, pause,
 * speed and seeking. The platform subclass builds the fake connection and applies packets.
 *
 * <p>Time model: every client tick plays 50 ms of the recording. Speed changes the client tick
 * rate, pause freezes it. A tick at replay time T shows the world as it was at T, and frames in
 * between interpolate from T - 50 to T like the game does with a live server.
 */
public abstract class AbstractReplay implements ReplayBackend {
    public static final int TICK_MS = 50;
    /** Time spent applying packets per frame while seeking, so the game stays responsive. */
    private static final long SEEK_BUDGET_NS = 30_000_000L;

    protected final RedoArchive archive;
    private PacketStreamReader reader;
    private PacketRecord next;
    private long tickTime;
    private boolean paused = true;
    private double speed = 1;
    private long seekTarget = -1;
    private CameraState camera;
    private boolean exporting;
    private boolean failed;
    /** Time of the first packet of the game itself (the world exists from there on). */
    private long startMs;
    /** Interpolation shown while frozen (paused or seeking), so a paused frame is exact. */
    private float pausedPartial = 1;

    protected AbstractReplay(RedoArchive archive) {
        this.archive = archive;
    }

    // ------------------------------------------------------------------ platform side

    /** Builds a fresh client world fed by a fake connection, ready for the first packet. */
    protected abstract void openWorld() throws Exception;

    /** Tears down the current world without leaving the replay (before a backward jump). */
    protected abstract void closeWorld();

    /** Decodes and applies one recorded packet on the main thread. */
    protected abstract void handle(PacketRecord p) throws Exception;

    /** Applies pause and speed to the game's tick timer. */
    protected abstract void applyTiming(boolean frozen, double speed);

    /** The game's own interpolation between the previous and the current tick, 0..1. */
    protected abstract float livePartialTick();

    /** Called after a jump so entities snap to where they are instead of gliding there. */
    protected void afterJump() {
    }

    // ------------------------------------------------------------------ lifecycle

    /** Opens the packet stream and the world. Seeks a little so the world is loaded. */
    public void start() throws Exception {
        Recorder.setReplaying(true);
        startMs = findStart();
        reader = archive.openPackets();
        next = reader.next();
        tickTime = 0;
        openWorld();
        seekTarget = Math.min(startMs + 1000, durationMs());
    }

    /**
     * The recording begins while the client is still logging in (and configuring, on recent
     * versions): before the first packet of the game, there is no world to show.
     */
    private long findStart() throws IOException {
        PacketStreamReader r = archive.openPackets();
        try {
            for (PacketRecord p = r.next(); p != null; p = r.next()) {
                if (p.phase == PacketRecord.PHASE_PLAY) return Math.min(p.timeMs, durationMs());
            }
            return 0;
        } finally {
            r.close();
        }
    }

    /** The earliest replay time that shows a world; seeks before it land on it. */
    public long startMs() {
        return startMs;
    }

    /** Releases the packet stream (the platform has already left the world). */
    public void dispose() {
        try {
            if (reader != null) reader.close();
        } catch (IOException ignored) {
        }
        reader = null;
        Recorder.setReplaying(false);
    }

    public boolean failed() {
        return failed;
    }

    private void apply(PacketRecord p) {
        try {
            handle(p);
        } catch (Exception e) {
            RedoLog.error("Replay packet failed at " + p.timeMs + " ms", e);
        }
    }

    private void advance() {
        try {
            next = reader == null ? null : reader.next();
        } catch (IOException e) {
            RedoLog.error("Could not read the recording", e);
            failed = true;
            next = null;
        }
    }

    // ------------------------------------------------------------------ driving

    /** Called by the platform once per rendered frame, before the world is drawn. */
    public void frame() {
        if (seekTarget >= 0) {
            long deadline = System.nanoTime() + SEEK_BUDGET_NS;
            while (next != null && next.timeMs <= seekTarget && System.nanoTime() < deadline) {
                apply(next);
                advance();
            }
            if (next == null || next.timeMs > seekTarget) {
                tickTime = alignUp(seekTarget);
                while (next != null && next.timeMs <= tickTime) {
                    apply(next);
                    advance();
                }
                pausedPartial = clamp01((seekTarget - (tickTime - TICK_MS)) / (float) TICK_MS);
                seekTarget = -1;
                afterJump();
            }
        }
        applyTiming(isFrozen(), exporting ? 1 : speed);
    }

    /** Called by the platform at the start of each client tick. */
    public void tick() {
        if (seekTarget >= 0 || (paused && !exporting)) return;
        if (!exporting && tickTime >= durationMs()) {
            paused = true;
            return;
        }
        tickTime += TICK_MS;
        while (next != null && next.timeMs <= tickTime) {
            apply(next);
            advance();
        }
    }

    /** True while the game must not tick (paused or seeking, outside export). */
    public boolean isFrozen() {
        return !exporting && (paused || seekTarget >= 0);
    }

    /** Interpolation the game should use for this frame. */
    public float renderPartial() {
        return isFrozen() ? pausedPartial : livePartialTick();
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static long alignUp(long t) {
        return ((t + TICK_MS - 1) / TICK_MS) * TICK_MS;
    }

    // ------------------------------------------------------------------ export timing

    /** Export mode: the platform runs exactly the ticks asked by {@link #exportStep}. */
    public void setExporting(boolean e) {
        exporting = e;
    }

    public boolean isExporting() {
        return exporting;
    }

    /**
     * For a frame of the export showing replay time {@code t}: how many ticks the game must run
     * now (and {@code partial[0]} the interpolation), or -1 if a jump is needed first.
     */
    public int exportStep(double t, float[] partial) {
        if (seekTarget >= 0) return -1;
        long needed = (long) Math.floor(t / TICK_MS) * TICK_MS + TICK_MS;
        int ticks = (int) ((needed - tickTime) / TICK_MS);
        partial[0] = (float) ((t - (needed - TICK_MS)) / TICK_MS);
        if (ticks < 0 || ticks > 10) return -1;
        return ticks;
    }

    // ------------------------------------------------------------------ ReplayBackend

    @Override
    public long durationMs() {
        return Math.max(archive.meta.durationMs, 1);
    }

    @Override
    public double timeMs() {
        if (seekTarget >= 0) return seekTarget;
        return Math.max(0, tickTime - TICK_MS + renderPartial() * TICK_MS);
    }

    /** Replay time of the last tick. */
    public long tickTime() {
        return tickTime;
    }

    @Override
    public boolean isSeeking() {
        return seekTarget >= 0;
    }

    @Override
    public void seek(long t) {
        t = Math.max(startMs, Math.min(t, durationMs()));
        if (seekTarget >= 0 && t >= seekTarget) {
            seekTarget = t;
            return;
        }
        long shown = (long) timeMs();
        if (t >= tickTime && seekTarget < 0) {
            if (t - shown < 1) return;
            seekTarget = t;
            return;
        }
        if (seekTarget < 0 && t >= tickTime - TICK_MS && t < tickTime) {
            // inside the current tick: nothing to replay, the interpolation shows it
            seekTarget = t;
            return;
        }
        // backwards: replay from the start
        closeWorld();
        try {
            if (reader != null) reader.close();
            reader = archive.openPackets();
            next = reader.next();
            tickTime = 0;
            openWorld();
        } catch (Exception e) {
            RedoLog.error("Could not restart the replay", e);
            failed = true;
            return;
        }
        seekTarget = t;
    }

    @Override
    public void setPaused(boolean p) {
        if (p && !paused && seekTarget < 0) pausedPartial = clamp01(livePartialTick());
        paused = p;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public void setSpeed(double s) {
        speed = Math.max(0.05, Math.min(s, 16));
    }

    @Override
    public double speed() {
        return speed;
    }

    @Override
    public void setCamera(CameraState c) {
        camera = c;
    }

    /** The pose forced by the editor, or null to leave the game camera alone. */
    public CameraState camera() {
        return camera;
    }
}
