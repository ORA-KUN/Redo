package fr.orakun.redo.core.format;

import fr.orakun.redo.core.io.VarIO;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Writes packet records to a deflate stream. The stream is sync-flushed regularly, so a crash
 * only loses the last few seconds.
 */
public final class PacketStreamWriter implements Closeable {
    private final DataOutputStream out;
    private final Deflater deflater;
    private int lastTime;
    private long count;
    private long lastFlush = System.currentTimeMillis();

    public PacketStreamWriter(File file) throws IOException {
        deflater = new Deflater(5);
        out = new DataOutputStream(new DeflaterOutputStream(
                new BufferedOutputStream(new FileOutputStream(file), 1 << 16), deflater, 1 << 16, true));
    }

    public void write(int timeMs, int phase, byte[] data, int off, int len) throws IOException {
        int dt = Math.max(0, timeMs - lastTime);
        lastTime = Math.max(lastTime, timeMs);
        VarIO.writeVarInt(out, dt);
        out.writeByte(phase);
        VarIO.writeVarInt(out, len);
        out.write(data, off, len);
        count++;
        maybeFlush();
    }

    public void maybeFlush() throws IOException {
        long now = System.currentTimeMillis();
        if (now - lastFlush > 3000) {
            out.flush();
            lastFlush = now;
        }
    }

    public long count() {
        return count;
    }

    public int lastTime() {
        return lastTime;
    }

    @Override
    public void close() throws IOException {
        out.close();
        deflater.end();
    }
}
