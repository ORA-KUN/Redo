package fr.orakun.redo.core.export;

import fr.orakun.redo.core.replay.AbstractReplay;

/**
 * Frame-by-frame control of an export, shared by the platforms. Each game frame:
 * {@link #beginFrame()} before the tick timer runs, then the platform forces the timer to
 * {@link #forcedTicks()} / {@link #forcedPartial()}, renders the world and, if
 * {@link #captureThisFrame()}, reads the image back and hands it to {@link ExportJob#submit}.
 * Read-backs may complete a few frames later; they arrive in order.
 */
public final class ExportDriver {
    private static final int MAX_IN_FLIGHT = 3;

    public final ExportJob job;
    private final AbstractReplay replay;
    private final float[] partial = new float[1];
    private int planned;
    private int forcedTicks;
    private boolean capture;
    private byte[] black;

    public ExportDriver(ExportJob job, AbstractReplay replay) {
        this.job = job;
        this.replay = replay;
    }

    public boolean isActive() {
        return job.isActive();
    }

    public void beginFrame() {
        capture = false;
        forcedTicks = 0;
        if (!job.isActive()) {
            replay.setExporting(false);
            return;
        }
        replay.setExporting(true);
        int inFlight = planned - job.frame();
        if (inFlight >= MAX_IN_FLIGHT) return;
        ExportJob.Target t = job.target(planned);
        if (t.replayTimeMs < 0) {
            // gap in the timeline: black frame, written once earlier frames are in
            if (inFlight == 0) {
                if (black == null) black = new byte[job.inputWidth() * job.inputHeight() * 4];
                job.submit(black);
                planned++;
            }
            return;
        }
        replay.setCamera(t.camera);
        int n = replay.exportStep(t.replayTimeMs, partial);
        if (n < 0) {
            replay.seek((long) t.replayTimeMs);
            return;
        }
        forcedTicks = n;
        capture = true;
    }

    /** Ticks the game must run this frame (0 when waiting). */
    public int forcedTicks() {
        return forcedTicks;
    }

    public float forcedPartial() {
        return partial[0];
    }

    public boolean captureThisFrame() {
        return capture;
    }

    /** The platform has requested the read-back of this frame. */
    public void captured() {
        capture = false;
        planned++;
    }

    public void stop() {
        replay.setExporting(false);
    }
}
