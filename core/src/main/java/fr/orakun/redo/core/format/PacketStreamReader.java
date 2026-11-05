package fr.orakun.redo.core.format;

import fr.orakun.redo.core.io.VarIO;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

/** Sequential reader for packets.bin. A truncated tail ends the stream quietly. */
public final class PacketStreamReader implements Closeable {
    private final DataInputStream in;
    private int time;
    private PacketRecord peeked;
    private boolean eof;

    public PacketStreamReader(InputStream raw) {
        this.in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(raw), 1 << 16));
    }

    public PacketRecord peek() throws IOException {
        if (peeked == null && !eof) peeked = read();
        return peeked;
    }

    public PacketRecord next() throws IOException {
        PacketRecord r = peek();
        peeked = null;
        return r;
    }

    private PacketRecord read() throws IOException {
        try {
            int dt = VarIO.readVarInt(in);
            int phase = in.readUnsignedByte();
            int len = VarIO.readVarInt(in);
            if (len < 0 || len > (1 << 24)) throw new IOException("Paquet invalide (" + len + " octets)");
            byte[] data = new byte[len];
            in.readFully(data);
            time += dt;
            return new PacketRecord(time, phase, data);
        } catch (EOFException e) {
            eof = true;
            return null;
        } catch (java.util.zip.ZipException e) {
            // stream cut by a crash: treat as the end of the recording
            eof = true;
            return null;
        }
    }

    public boolean isEof() {
        return eof && peeked == null;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
