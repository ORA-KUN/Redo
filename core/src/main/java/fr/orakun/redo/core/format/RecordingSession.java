package fr.orakun.redo.core.format;

import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.json.Json;
import fr.orakun.redo.core.timeline.Marker;
import fr.orakun.redo.core.track.EntityInfo;
import fr.orakun.redo.core.track.EntitySample;
import fr.orakun.redo.core.track.GameEvent;
import fr.orakun.redo.core.track.SelfEquipment;
import fr.orakun.redo.core.track.SelfSample;
import fr.orakun.redo.core.track.TerrainSample;
import fr.orakun.redo.core.track.TrackCodec;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * A recording in progress. Packets arrive on the network thread and track data on the game
 * thread; both are queued to a single writer thread so the game never waits on the disk.
 * Data goes to a temporary folder and is packed into a .redo archive when the session stops.
 */
public final class RecordingSession {
    public interface Done {
        void finished(File archive, Exception error);
    }

    private interface Job {
        void run() throws IOException;
    }

    private static final Job STOP = new Job() {
        @Override
        public void run() {
        }
    };

    public final RedoMeta meta;
    public final File tempDir;
    private final File recordingsDir;
    private final long startNanos = System.nanoTime();
    private final LinkedBlockingQueue<Job> queue = new LinkedBlockingQueue<Job>(200000);
    private final PacketStreamWriter packets;
    private final DataOutputStream track;
    private final Thread writer;
    private volatile boolean stopped;
    private volatile boolean overflowed;
    private long highlightStart = -1;
    private volatile long packetBytes;

    public RecordingSession(File recordingsDir, RedoMeta meta) throws IOException {
        this.recordingsDir = recordingsDir;
        this.meta = meta;
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        this.tempDir = new File(recordingsDir, ".tmp-" + stamp + "-" + Integer.toHexString((int) (Math.random() * 0xFFFF)));
        if (!tempDir.mkdirs()) throw new IOException("Impossible de créer " + tempDir);
        meta.startedAt = System.currentTimeMillis();
        packets = new PacketStreamWriter(new File(tempDir, RedoArchive.PACKETS));
        track = new DataOutputStream(new DeflaterOutputStream(new BufferedOutputStream(
                new FileOutputStream(new File(tempDir, RedoArchive.TRACK)), 1 << 16), new Deflater(6), 1 << 15, true));
        writeMeta();
        writer = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "Redo recorder");
        writer.setDaemon(true);
        writer.start();
    }

    /** Milliseconds since the start of the recording. */
    public int timeMs() {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000L);
    }

    public boolean isStopped() {
        return stopped;
    }

    public long packetBytes() {
        return packetBytes;
    }

    private void enqueue(Job j) {
        if (stopped) return;
        if (!queue.offer(j)) {
            if (!overflowed) RedoLog.warn("Recorder queue full, dropping data");
            overflowed = true;
        }
    }

    private void loop() {
        long lastMeta = System.currentTimeMillis();
        try {
            while (true) {
                Job j = queue.take();
                if (j == STOP) break;
                try {
                    j.run();
                } catch (IOException e) {
                    RedoLog.error("Recorder write failed", e);
                }
                if (System.currentTimeMillis() - lastMeta > 10000) {
                    meta.durationMs = timeMs();
                    meta.packetCount = packets.count();
                    writeMeta();
                    track.flush();
                    lastMeta = System.currentTimeMillis();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            RedoLog.error("Recorder failed", e);
        }
    }

    // ---------------------------------------------------------------- inputs

    /** Called from the network thread with the decompressed bytes of one clientbound packet. */
    public void packet(final int phase, final byte[] data) {
        final int t = timeMs();
        packetBytes += data.length;
        enqueue(new Job() {
            @Override
            public void run() throws IOException {
                packets.write(t, phase, data, 0, data.length);
            }
        });
    }

    public void entity(final EntityInfo e) {
        enqueue(new Job() {
            @Override
            public void run() throws IOException {
                TrackCodec.writeEntity(track, e);
            }
        });
    }

    public void sample(final int entityId, final EntitySample s) {
        enqueue(new Job() {
            @Override
            public void run() throws IOException {
                TrackCodec.writeSample(track, entityId, s);
            }
        });
    }

    public void event(final GameEvent e) {
        enqueue(new Job() {
            @Override
            public void run() throws IOException {
                TrackCodec.writeEvent(track, e);
            }
        });
    }

    public void terrain(final TerrainSample t) {
        enqueue(new Job() {
            @Override
            public void run() throws IOException {
                TrackCodec.writeTerrain(track, t);
            }
        });
    }

    public void self(final SelfSample s) {
        enqueue(new Job() {
            @Override
            public void run() throws IOException {
                TrackCodec.writeSelf(track, s);
            }
        });
    }

    public void equipment(final SelfEquipment e) {
        enqueue(new Job() {
            @Override
            public void run() throws IOException {
                TrackCodec.writeEquipment(track, e);
            }
        });
    }

    public void removed(final int entityId) {
        final int t = timeMs();
        enqueue(new Job() {
            @Override
            public void run() throws IOException {
                TrackCodec.writeRemove(track, t, entityId);
            }
        });
    }

    public synchronized void marker(String label) {
        int t = timeMs();
        meta.markers.add(new Marker(t, label, "manual"));
        event(new GameEvent(GameEvent.Type.MARKER, t, GameEvent.NONE, GameEvent.NONE, 0, 0, 0, label));
    }

    /** Starts or ends a "passage à garder". Returns true if a highlight is now open. */
    public synchronized boolean toggleHighlight() {
        int t = timeMs();
        if (highlightStart < 0) {
            highlightStart = t;
            event(new GameEvent(GameEvent.Type.HIGHLIGHT_START, t, GameEvent.NONE, GameEvent.NONE, 0, 0, 0, ""));
            return true;
        }
        meta.highlights.add(new long[]{highlightStart, t});
        event(new GameEvent(GameEvent.Type.HIGHLIGHT_END, t, GameEvent.NONE, GameEvent.NONE, 0, 0, 0, ""));
        highlightStart = -1;
        return false;
    }

    public synchronized boolean highlightOpen() {
        return highlightStart >= 0;
    }

    public boolean wasUsed() {
        return !meta.highlights.isEmpty() || !meta.markers.isEmpty() || highlightStart >= 0;
    }

    private synchronized void writeMeta() {
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(new File(tempDir, RedoArchive.META)), "UTF-8");
            try {
                w.write(Json.pretty(meta.toJson()));
            } finally {
                w.close();
            }
        } catch (IOException e) {
            RedoLog.error("Could not write meta.json", e);
        }
    }

    // ---------------------------------------------------------------- end

    /**
     * Stops recording. If {@code keep} is false the temporary data is deleted. Otherwise it is
     * packed into an archive in a background thread and {@code done} is called afterwards.
     */
    public void stop(final boolean keep, final String baseName, final Done done) {
        if (stopped) return;
        synchronized (this) {
            if (highlightStart >= 0) toggleHighlight();
        }
        final int end = timeMs();
        queue.offer(STOP);
        stopped = true;
        Thread finisher = new Thread(new Runnable() {
            @Override
            public void run() {
                File archive = null;
                Exception error = null;
                try {
                    writer.join(30000);
                    packets.close();
                    track.close();
                    meta.durationMs = end;
                    meta.packetCount = packets.count();
                    writeMeta();
                    if (keep && packets.count() > 0) {
                        archive = RedoArchive.pack(tempDir, recordingsDir, baseName);
                    }
                    RedoArchive.deleteRecursively(tempDir);
                } catch (Exception e) {
                    error = e;
                    RedoLog.error("Could not finish recording", e);
                }
                if (done != null) done.finished(archive, error);
            }
        }, "Redo recording finisher");
        finisher.setDaemon(false);
        finisher.start();
    }
}
