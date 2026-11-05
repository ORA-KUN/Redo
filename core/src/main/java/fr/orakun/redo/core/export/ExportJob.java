package fr.orakun.redo.core.export;

import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.timeline.Clip;
import fr.orakun.redo.core.timeline.ExportSettings;
import fr.orakun.redo.core.timeline.Sequence;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Frame-by-frame export of a sequence. The platform drives it from its render loop:
 * {@link #target()} says which replay time and camera to render, the platform renders the world
 * at exactly that moment, then hands the pixels to {@link #submit(ByteBuffer)}.
 */
public final class ExportJob {
    public enum State { RENDERING, FINISHING, DONE, FAILED, CANCELLED }

    public static final class Target {
        public final int frame;
        public final double timelineMs;
        public final double replayTimeMs;
        public final CameraState camera;
        /** 0 = normal, 1 = black. */
        public final double fade;
        public final Clip clip;

        Target(int frame, double timelineMs, double replayTimeMs, CameraState camera, double fade, Clip clip) {
            this.frame = frame;
            this.timelineMs = timelineMs;
            this.replayTimeMs = replayTimeMs;
            this.camera = camera;
            this.fade = fade;
            this.clip = clip;
        }
    }

    public final Sequence sequence;
    public final ExportSettings settings;
    public final File output;
    private final File ffmpeg;
    private final int inW, inH;
    private final int frameCount;
    private FfmpegEncoder encoder;
    private int frame;
    private volatile State state = State.RENDERING;
    private volatile String error;
    private final long startedAt = System.currentTimeMillis();
    private Target target;
    private boolean bottomUp = true;

    public ExportJob(Sequence sequence, ExportSettings settings, File ffmpeg, File output, int framebufferW, int framebufferH) {
        this.sequence = sequence;
        this.settings = settings;
        this.ffmpeg = ffmpeg;
        this.output = output;
        this.inW = framebufferW;
        this.inH = framebufferH;
        this.frameCount = Math.max(1, (int) Math.ceil(sequence.duration() * settings.fps / 1000.0));
    }

    /** Whether frames come bottom-up (OpenGL read) or top-down (already flipped images). */
    public void setBottomUp(boolean b) {
        bottomUp = b;
    }

    /** Starts ffmpeg. Returns false (and sets the error) if it cannot start. */
    public boolean start() {
        try {
            File parent = output.getParentFile();
            if (parent != null) parent.mkdirs();
            encoder = new FfmpegEncoder(ffmpeg, settings.format, settings.crf, settings.fps, inW, inH,
                    settings.width, settings.height, output, bottomUp);
            return true;
        } catch (IOException e) {
            fail("Impossible de lancer ffmpeg : " + e.getMessage());
            return false;
        }
    }

    public State state() {
        return state;
    }

    public String error() {
        return error;
    }

    public boolean isActive() {
        return state == State.RENDERING;
    }

    public int frame() {
        return frame;
    }

    public int frameCount() {
        return frameCount;
    }

    public double progress() {
        return frame / (double) frameCount;
    }

    public int inputWidth() {
        return inW;
    }

    public int inputHeight() {
        return inH;
    }

    /** Estimated remaining time in ms, or -1 before the first frames. */
    public long remainingMs() {
        if (frame < 10) return -1;
        long spent = System.currentTimeMillis() - startedAt;
        return (long) (spent / (double) frame * (frameCount - frame));
    }

    /** What to render for the current frame. */
    public Target target() {
        if (target != null && target.frame == frame) return target;
        target = target(frame);
        return target;
    }

    /** What to render for any frame of the video. */
    public Target target(int index) {
        double t = index * 1000.0 / settings.fps;
        Sequence.Frame f = sequence.evaluate(Math.min(t, Math.max(0, sequence.duration() - 1)));
        if (f == null || f.clip == null) return new Target(index, t, -1, null, 1, null);
        return new Target(index, t, f.replayTimeMs, f.camera, f.fade, f.clip);
    }

    /** Hands over the pixels of the current frame (RGBA, bottom-up rows as read from OpenGL). */
    public void submit(ByteBuffer rgba) {
        if (state != State.RENDERING) return;
        double fade = target().fade;
        if (fade > 0.001) {
            int k = (int) Math.round((1 - Math.min(1, fade)) * 256);
            for (int i = rgba.position(); i + 3 < rgba.limit(); i += 4) {
                rgba.put(i, (byte) (((rgba.get(i) & 0xFF) * k) >> 8));
                rgba.put(i + 1, (byte) (((rgba.get(i + 1) & 0xFF) * k) >> 8));
                rgba.put(i + 2, (byte) (((rgba.get(i + 2) & 0xFF) * k) >> 8));
            }
        }
        try {
            encoder.writeFrame(rgba);
            frame++;
            if (frame >= frameCount) finish();
        } catch (IOException e) {
            fail("Erreur d'écriture vidéo : " + e.getMessage());
        }
    }

    public void submit(byte[] rgba) {
        if (state != State.RENDERING) return;
        double fade = target().fade;
        if (fade > 0.001) {
            int k = (int) Math.round((1 - Math.min(1, fade)) * 256);
            for (int i = 0; i + 3 < rgba.length; i += 4) {
                rgba[i] = (byte) (((rgba[i] & 0xFF) * k) >> 8);
                rgba[i + 1] = (byte) (((rgba[i + 1] & 0xFF) * k) >> 8);
                rgba[i + 2] = (byte) (((rgba[i + 2] & 0xFF) * k) >> 8);
            }
        }
        try {
            encoder.writeFrame(rgba);
            frame++;
            if (frame >= frameCount) finish();
        } catch (IOException e) {
            fail("Erreur d'écriture vidéo : " + e.getMessage());
        }
    }

    /** A buffer of the right size for {@link #submit(byte[])}. */
    public byte[] borrowBuffer() {
        return encoder.borrowBuffer();
    }

    private void finish() {
        state = State.FINISHING;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    encoder.finish();
                    state = State.DONE;
                    RedoLog.info("Export done: " + output);
                } catch (IOException e) {
                    fail("ffmpeg a échoué : " + e.getMessage());
                }
            }
        }, "Redo export finish");
        t.setDaemon(true);
        t.start();
    }

    public void cancel() {
        if (state == State.RENDERING || state == State.FINISHING) {
            state = State.CANCELLED;
            if (encoder != null) encoder.abort();
        }
    }

    private void fail(String message) {
        error = message;
        state = State.FAILED;
        RedoLog.warn(message);
        if (encoder != null) encoder.abort();
    }
}
