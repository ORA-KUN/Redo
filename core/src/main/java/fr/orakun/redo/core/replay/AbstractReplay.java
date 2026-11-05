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
    private boolean restarting;
    private CameraState camera;
    private boolean exporting;
    private boolean failed;
    /** Time of the first packet of the game itself (the world exists from there on). */
    private long startMs;
    /** Interpolation shown while frozen (paused or seeking), so a paused frame is exact. */
    private float pausedPartial = 1;
    /** The last image of the world stays on screen while a jump replays packets (see isHoldingImage). */
    private boolean holding;
    private long holdSince, settledSince = -1;
    private int settledFrames;
    /** Longest time the image is kept once the packets are applied, if the terrain never settles. */
    private static final long HOLD_AFTER_MS = 2500;
    /** Longest hold in all, so a very long jump still shows that something happens. */
    private static final long HOLD_MAX_MS = 20000;
    /** A jump waiting for the image to be copied before it changes the world, or -1. */
    private long deferredTarget = -1;
    private long deferredSince;
    /** Longest wait for that copy. */
    private static final long IMAGE_WAIT_MS = 500;

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

    /** How the platform keeps the last image of the world when a jump starts. */
    protected enum ImageCapture {
        /** It cannot: the jump is seen as it happens. */
        NONE,
        /** The image is there already (copied with every drawn world). */
        READY,
        /** A copy has been asked for and arrives in a frame or two: the jump waits for it. */
        PENDING
    }

    /**
     * Keeps the image of the world now on screen, to show it while a jump rebuilds the world. Called
     * when a jump starts, during the editor's drawing (the world is drawn, the interface not yet).
     */
    protected ImageCapture captureImage() {
        return ImageCapture.NONE;
    }

    /** True once a {@link ImageCapture#PENDING} copy has arrived. */
    protected boolean imageReady() {
        return true;
    }

    /** True once the world around the camera is fully drawn (no terrain left to build). */
    protected boolean terrainReady() {
        return true;
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
        // a recording started from the pause menu opens where the user started it
        long clip = Math.max(0, Math.min(archive.meta.clipStartMs, durationMs()));
        PacketStreamReader r = archive.openPackets();
        try {
            for (PacketRecord p = r.next(); p != null; p = r.next()) {
                if (p.phase == PacketRecord.PHASE_PLAY) return Math.max(clip, Math.min(p.timeMs, durationMs()));
            }
            return clip;
        } finally {
            r.close();
        }
    }

    /** The earliest replay time that shows a world; seeks before it land on it. */
    @Override
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
        if (deferredTarget >= 0) {
            if (!imageReady() && System.currentTimeMillis() - deferredSince < IMAGE_WAIT_MS) {
                applyTiming(isFrozen(), speed);
                return;
            }
            // the image is kept: the world can change behind it
            long t = deferredTarget;
            deferredTarget = -1;
            seekTarget = t;
            if (t < tickTime - TICK_MS) restart(t);
        }
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
        updateHold();
        applyTiming(isFrozen(), exporting ? 1 : speed);
    }

    /** Lets go of the kept image a few frames after the jump, once the terrain is drawn. */
    private void updateHold() {
        if (!holding) return;
        long now = System.currentTimeMillis();
        if (exporting || now - holdSince > HOLD_MAX_MS) {
            holding = false;
            return;
        }
        if (seekTarget >= 0) {
            settledSince = -1;
            settledFrames = 0;
            return;
        }
        if (settledSince < 0) settledSince = now;
        // a couple of frames for the game to see the new world and queue its terrain
        if (++settledFrames > 3 && (terrainReady() || now - settledSince > HOLD_AFTER_MS)) holding = false;
    }

    /**
     * Starts keeping the current image, at the start of a jump that changes what is seen. Returns
     * true if the jump must wait for the copy of the image (see {@link #frame}).
     */
    private boolean hold() {
        if (holding || exporting) return false;
        ImageCapture c = captureImage();
        if (c == ImageCapture.NONE) return false;
        holding = true;
        holdSince = System.currentTimeMillis();
        settledSince = -1;
        settledFrames = 0;
        if (c != ImageCapture.PENDING) return false;
        deferredSince = holdSince;
        return true;
    }

    @Override
    public boolean isHoldingImage() {
        return holding;
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
        t = Math.max(t, startMs); // before the world exists, the first moment of it is shown
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
        if (deferredTarget >= 0) {
            // still waiting for the image: the world has not changed yet, only the aim does
            deferredTarget = t;
            seekTarget = t;
            return;
        }
        if (seekTarget >= 0 && t >= seekTarget) {
            seekTarget = t;
            if (t - tickTime > 4 * TICK_MS && hold()) deferredTarget = t;
            return;
        }
        long shown = (long) timeMs();
        if (t >= tickTime && seekTarget < 0) {
            if (t - shown < 1) return;
            seekTarget = t;
            // a short step forward is applied within the next frame: nothing to hide
            if (t - tickTime > 4 * TICK_MS && hold()) deferredTarget = t;
            return;
        }
        if (seekTarget < 0 && t >= tickTime - TICK_MS && t < tickTime) {
            // inside the current tick: nothing to replay, the interpolation shows it
            seekTarget = t;
            return;
        }
        // backwards: replay from the start. The game may draw the open screen (the editor) while
        // the world is torn down: it sees a seek in progress and does not ask for another one.
        seekTarget = t;
        if (restarting) return;
        if (hold()) {
            deferredTarget = t;
            return;
        }
        restart(t);
    }

    /** Rebuilds the world from the first packet, for a jump to {@code t} behind the current time. */
    private void restart(long t) {
        if (restarting) return;
        restarting = true;
        try {
            closeWorld();
            if (reader != null) reader.close();
            reader = archive.openPackets();
            next = reader.next();
            tickTime = 0;
            openWorld();
        } catch (Exception e) {
            RedoLog.error("Could not restart the replay", e);
            failed = true;
            return;
        } finally {
            restarting = false;
        }
        if (seekTarget < 0) seekTarget = t;
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
