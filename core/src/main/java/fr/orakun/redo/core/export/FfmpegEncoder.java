package fr.orakun.redo.core.export;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Pipes raw RGBA frames (bottom-up, as read from OpenGL) into an ffmpeg process.
 * Frames are handed to a writer thread so rendering and encoding overlap.
 */
public final class FfmpegEncoder {
    private static final byte[] POISON = new byte[0];

    private final Process process;
    private final OutputStream stdin;
    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<byte[]>(6);
    private final Deque<byte[]> pool = new ArrayDeque<byte[]>();
    private final List<String> log = new ArrayList<String>();
    private final Thread writer;
    private final int frameBytes;
    private volatile IOException failure;
    private long frames;

    public final File output;

    /**
     * @param inW,inH  size of the frames that will be written
     * @param outW,outH size of the video; the image is scaled to cover it and centre-cropped
     */
    public FfmpegEncoder(File ffmpeg, ExportFormat format, int crf, int fps, int inW, int inH, int outW, int outH,
                         File output) throws IOException {
        this.output = output;
        this.frameBytes = inW * inH * 4;
        List<String> cmd = new ArrayList<String>();
        cmd.add(ffmpeg.getAbsolutePath());
        cmd.add("-y");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("warning");
        cmd.add("-f");
        cmd.add("rawvideo");
        cmd.add("-pix_fmt");
        cmd.add("rgba");
        cmd.add("-s");
        cmd.add(inW + "x" + inH);
        cmd.add("-r");
        cmd.add(String.valueOf(fps));
        cmd.add("-i");
        cmd.add("-");
        cmd.add("-vf");
        cmd.add(filter(inW, inH, outW, outH));
        cmd.addAll(format.encoderArgs(crf));
        if (format.isSequence()) {
            if (!output.isDirectory() && !output.mkdirs()) throw new IOException("Impossible de créer " + output);
            cmd.add(new File(output, "frame_%06d.png").getAbsolutePath());
        } else {
            File parent = output.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Impossible de créer " + parent);
            cmd.add(output.getAbsolutePath());
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        process = pb.start();
        stdin = process.getOutputStream();
        Thread logger = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) {
                        synchronized (log) {
                            log.add(line);
                            if (log.size() > 40) log.remove(0);
                        }
                    }
                } catch (IOException ignored) {
                    // process ended
                }
            }
        }, "Redo ffmpeg log");
        logger.setDaemon(true);
        logger.start();
        writer = new Thread(new Runnable() {
            @Override
            public void run() {
                writeLoop();
            }
        }, "Redo ffmpeg writer");
        writer.setDaemon(true);
        writer.start();
    }

    static String filter(int inW, int inH, int outW, int outH) {
        StringBuilder vf = new StringBuilder("vflip");
        if (inW != outW || inH != outH) {
            double scale = Math.max(outW / (double) inW, outH / (double) inH);
            int sw = even((int) Math.ceil(inW * scale));
            int sh = even((int) Math.ceil(inH * scale));
            vf.append(String.format(Locale.ROOT, ",scale=%d:%d:flags=lanczos,crop=%d:%d", sw, sh, even(outW), even(outH)));
        }
        return vf.toString();
    }

    private static int even(int v) {
        return v % 2 == 0 ? v : v + 1;
    }

    /** Returns a buffer of the right size to fill with the next frame (reuses old ones). */
    public byte[] borrowBuffer() {
        synchronized (pool) {
            byte[] b = pool.poll();
            if (b != null) return b;
        }
        return new byte[frameBytes];
    }

    public void writeFrame(byte[] rgba) throws IOException {
        if (failure != null) throw failure;
        if (rgba.length != frameBytes) throw new IOException("Taille d'image inattendue : " + rgba.length + " au lieu de " + frameBytes);
        try {
            while (!queue.offer(rgba, 200, TimeUnit.MILLISECONDS)) {
                if (failure != null) throw failure;
                if (!process.isAlive()) throw new IOException("ffmpeg s'est arrêté : " + lastLog());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Export interrompu");
        }
        frames++;
    }

    public void writeFrame(ByteBuffer rgba) throws IOException {
        byte[] b = borrowBuffer();
        rgba.get(b, 0, Math.min(b.length, rgba.remaining()));
        writeFrame(b);
    }

    private void writeLoop() {
        try {
            while (true) {
                byte[] f = queue.take();
                if (f == POISON) break;
                stdin.write(f);
                synchronized (pool) {
                    if (pool.size() < 8) pool.push(f);
                }
            }
            stdin.flush();
        } catch (IOException e) {
            failure = new IOException("ffmpeg a refusé les images : " + lastLog(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                stdin.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    public long frameCount() {
        return frames;
    }

    /** Waits for every frame to be encoded. */
    public void finish() throws IOException {
        try {
            queue.put(POISON);
            writer.join();
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroy();
                throw new IOException("ffmpeg ne répond plus");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroy();
            throw new IOException("Export interrompu");
        }
        if (failure != null) throw failure;
        if (process.exitValue() != 0) throw new IOException("ffmpeg a échoué (code " + process.exitValue() + ") : " + lastLog());
    }

    public void abort() {
        writer.interrupt();
        process.destroy();
    }

    public String lastLog() {
        synchronized (log) {
            StringBuilder sb = new StringBuilder();
            for (int i = Math.max(0, log.size() - 4); i < log.size(); i++) sb.append(log.get(i)).append(' ');
            return sb.toString().trim();
        }
    }
}
